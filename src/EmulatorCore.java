/* 
	Copyright 2013 Charles O.
	charles.0x4f@gmail.com
	Github: https://github.com/charles-0x4f

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Random;
import java.lang.Thread;

/*
	The core of the interpreter. All CPU-related things wll be here
*/
public class EmulatorCore {
	// CPU related
	short Opcode, I;
	byte[] memory, rom, V;
	byte[][] graphics;
	byte SP, key;
	int PC, stack[], delay_timer, sound_timer, instruction_count;

	// Peripheral to CPU
	Input input;
	Random gen;
	Sound sound;
	Thread inputThread, soundThread;

	// Instruction limiting related
	double rate, per, allowance;
	long current, passed, last_checked;

	// Constructor	
	public EmulatorCore(String romFile, Input inputObj) {
		// Seed our random to 567765, there was a technical
		// reason for this number but I forgot it.
		gen = new Random(567765);

		reset();
		
		if(!loadRom(romFile)) {
			// TODO Throw some exception
		}

		// Start input thread
		input = inputObj;

		// Sound initialization
		sound = new Sound();
		soundThread = new Thread(sound);
		soundThread.start();
	}
	
	/*
		Sets the virtual CPU to its initial state
	*/
	public void reset() {
		I = 0x0;
		// Allocate virtual memory, registers, VRAM and stack
		memory = new byte[4096];
		V = new byte[16];
		graphics = new byte[64][32];
		stack = new int[16];

		// Set key inputted to error/none
		key = -1;

		// Set timers
		delay_timer = 0;
		sound_timer = 0;

		// Reset instruction count
		instruction_count = 0;

		// Initialize instruction limiting
		// 14 instructions allowed
		rate = 14;
		// per 100 millisecond
		per = 100;
		allowance = rate;
		last_checked = System.currentTimeMillis();
		
		// ROM gets mapped to memory starting at 0x200
		PC = 0x200;

		// Set the font sprites
		int[] font = {
			0xF0, 0x90, 0x90, 0x90, 0xF0,
			0x20, 0x60, 0x20, 0x20, 0x70,
			0xF0, 0x10, 0xF0, 0x80, 0xF0,
			0xF0, 0x10, 0xF0, 0x10, 0xF0,
			0x90, 0x90, 0xF0, 0x10, 0x10,
			0xF0, 0x80, 0xF0, 0x10, 0xF0,
			0xF0, 0x80, 0xF0, 0x90, 0xF0,
			0xF0, 0x10, 0x20, 0x40, 0x40,
			0xF0, 0x90, 0xF0, 0x90, 0xF0,
			0xF0, 0x90, 0xF0, 0x10, 0xF0,
			0xF0, 0x90, 0xF0, 0x90, 0x90,
			0xE0, 0x90, 0xE0, 0x90, 0xE0,
			0xF0, 0x80, 0x80, 0x80, 0xF0,
			0xE0, 0x90, 0x90, 0x90, 0xE0,
			0xF0, 0x80, 0xF0, 0x80, 0xF0,
			0xF0, 0x80, 0xF0, 0x80, 0x80
		};

		// Load font sprite into memory map
		for(int x = 0; x < font.length; x++)
			memory[x] = (byte)font[x];
	}
	
	/*
		Loads passed ROM location into virtual memory (at 0x200).
	*/
	public boolean loadRom(String file) {
		InputStream stream;
	
		System.out.println("Hello?");	
		try {
			stream = new FileInputStream(file);

			// Get actual size of file	
			int c = 0, romSize = 0;
			for(int x = 0; c != -1; x++) {
				c = stream.read();
			
				// How many bytes are in the rom?
				if(c == -1) {
					romSize = x;

					break;
				}
			}
		
			rom = new byte[romSize];
			System.out.println("Rom size: " +
				String.format("%x", romSize));
		
			// Read file into rom
			stream = new FileInputStream(file);
			stream.read(rom, 0, romSize);

			// Copy rom into memory map starting at 0x200
			System.arraycopy(rom, 0, memory, 0x200, romSize);

			// Debug
			System.out.println("rom1: " + rom[0]);

			stream.close();
			
			return true;
		} catch(Exception e) {
			e.printStackTrace();

			return false;
		}
	}

	/*
		Returns the emulator core's VRAM.

		The Video class uses this to implement the graphics driver.
	*/
	public byte[][] getVRam() {
		return graphics;
	}

	/*
		Limit the number of instructions that can run in a given time
		frame.

		Algorithm from StackOverflow (slightly modified for milliseconds)
		http://stackoverflow.com/questions/667508/whats-a-good-rate-limiting-algorithm
	*/
	public boolean limiter() {
		// Get the current number of milliseconds that have passed
		// since an irrelevant amount of time
		current = System.currentTimeMillis();
		
		//System.out.println("Current: " + current);
		// Difference between our two clock times
		passed = current - last_checked;
		
		//if(passed > 0)
		//	System.out.println("Passed: " + passed);
		
		last_checked = current;
		allowance += passed * (rate / per);

		if(allowance > rate)
			allowance = rate;

		if(allowance < 1.0) {
			//System.out.println("LIMITED");
			return false;
		}
		else
		{
			allowance -= 1.0;
			return true;
		}
	}

	/*
		The controller of our interpreter's execution.

		This will implement instruciton limiting, fetch opcodes from
		memory, dispatch instruction execution, and update the delay
		and sound timers.
	*/
	public void cycle() {
		// Do instruction limiting so we don't run at light speed
		if(!limiter())
			return;

		// If we've hit the instruction limit, reset count so that
		// the timers can be updated
		if(instruction_count == 5)
			instruction_count = 0;

		System.out.println(String.format("\n\nPC@%x: %x; I: %x", PC, memory[PC], I));

		/*
			Java has no unsigned support and the conversion from
			bytes to shorts can get messed up so we're going to
			step around the issue here.
		*/
		int msb, lsb, total;

		msb = (((int)memory[PC]) & 0xFF);
		lsb = (((int)memory[PC + 1]) & 0xFF);

		// Get opcode
		//Opcode = (short)((memory[PC] << 8) | memory[PC + 1]);
		total = ((msb << 8) | lsb);
		Opcode = (short)total;
		
		System.out.println("\tOp: " + String.format("%x", Opcode));

		// Main execution switch
		Execute(Opcode);

		// Update timers
		// These only fire when instruction count is zero as this
		// allows us to limit the Hz that the interpreter runs at
		if(instruction_count == 0) {
			if(delay_timer > 0)
			{
				delay_timer--;
			}

			if(sound_timer > 0)
			{
				sound_timer--;

				if(sound_timer > 0)
				{
					System.out.println("SoundT: " + sound_timer);
					sound.setPlaying(true);
				}
				else
				{
					if(sound.isPlaying())
						sound.setPlaying(false);
				}
			}
		}

		// Update instruction count
		instruction_count++;
	}
	
	/*
		The executer of instructions.

		This method receives the opcode of an instruction and
		interprets/executes it.
	*/
	private void Execute(short OP) {
		/*
			These are used for code cleanliness and convenience.

			The opcodes have the instruciton arguments built into
			them and we need to extract them.

			Example:

			Opcode 0x1214(0x1NNN): Jump to address NNN(214)
			Not all will be used for every instruction
		*/
		int x = ((OP & 0x0F00) >> 8);
		int y = ((OP & 0x00F0) >> 4);
		int kk = (OP & 0x00FF);
		int nnn = (OP & 0x0FFF);

		//System.out.println(String.format("x: %x, y: %x, kk: %x, nnn: %x",
		//	x, y, kk, nnn));

		// Debug: Print V[x] registers
		for(int vx = 0; vx < 16; vx++)
		{
			if((vx % 8) == 0)
				System.out.println("\n\t\t");

			System.out.print("V["+vx+"] = " +
				String.format("%x ", V[vx]));
		}

		// Begin interpreter (get most significant bit)
		switch(OP & 0xF000) {
		
		// Instructions beggining with 0
		case 0x0000:
		{
			if(OP == 0x00E0) {
				/*
				 * 00E0 - CLS
				 * Clear the display.
				 */
				
				for(int pixelX = 0; pixelX < 64; pixelX++)
				{
					for(int pixelY = 0; pixelY < 32; pixelY++)
						graphics[pixelX][pixelY] = 0;
				}

				PC += 2;
			}	
			else if(OP == 0x00EE) {
				/*
				 * 00EE - RET
				 * Return from a subroutine.
				 */
				
				SP--;
				//System.out.println(String.format("Popb: %x", PC));
				PC = stack[SP];
				//System.out.println(String.format("Popa: %x", PC));

				PC += 2;
			}
			else {
				System.out.println("This instruction is to be ignored");
			}
			
			break;
		}
		
		// Instructions beginning with 1
		case 0x1000:
		{
			/*
			 * 1nnn - JP addr
			 * Jump to location nnn.
			 */
			
			PC = (short)(OP & 0x0FFF);
			
			break;
		}
			
		// Instructions beginning with 2
		case 0x2000:
		{
			/*
			 * 2nnn - CALL addr
			 * Call subroutine at nnn.
			 */
			//System.out.println("2000b: SP: " + SP + ", (SP): " + String.format("%x", SP, stack[SP]));
			stack[SP] = PC;
			SP++;
			//System.out.println("2000b: SP: " + SP + ", (SP): " + String.format("%x", SP, stack[SP]));

			PC = (short)(OP & 0x0FFF);
			
			break;
		}
		
		// Instructions beginning with 3
		case 0x3000:
		{
			/*
			 * 3xkk - SE Vx, byte
			 * Skip next instruction if Vx = kk.
			 */
			//System.out.println(String.format("3000: Vx: %x; kk: %x", V[x], kk));
	
			if(V[x] == (byte)kk) {
				//System.out.println("Skipped");
				PC += 4;
			}
			else {
				//System.out.println("Not skipped");
				PC += 2;
			}
			
			break;
		}
		
		// Instructions beginning with 4
		case 0x4000:
		{
			/*
			 * 4xkk - SNE Vx, byte
			 * Skip next instruction if Vx != kk.
			 */
			//System.out.println(String.format("4000: Vx: %x, kk: %x", V[x], kk));
			
			if(V[x] != (byte)kk) {
				//System.out.println("Skipped");
				PC += 4;
			}
			else {
				//System.out.println("Not skipped");
				PC += 2;
			}
			
			break;
		}
		
		// Instructions beginning with 5
		case 0x5000:
		{
			/*
			 * 5xy0 - SE Vx, Vy
			 * Skip next instruction if Vx = Vy.
			 */
			//System.out.println(String.format("Vx: %x; Vy: %x", V[x], V[y]));
			
			if(V[x] == V[y]) {
				//System.out.println("Skipped");
				PC += 4;
			}
			else {
				//System.out.println("Not skipped");
				PC += 2;
			}
			
			break;
		}
		
		// Instructions beginning with 6
		case 0x6000:
		{
			/*
			 * 6xkk - LD Vx, byte
			 * Set Vx = kk.
			 */
			//System.out.println(String.format("DBG6000: kk: %x", kk));			

			V[x] = (byte)kk;

			PC += 2;
			break;
		}
		
		// Instructions beginning with 7
		case 0x7000:
		{
			/*
			 * 7xkk - ADD Vx, byte
			 * Set Vx = Vx + kk.
			 */
			//System.out.println(String.format("7000b: %x, kk: %x", V[x], kk));

			V[x] = (byte)(V[x] + kk);
			//System.out.println(String.format("7000a: %x, kk: %x", V[x], kk));
			
			PC += 2;
			break;
		}
		
		// Instructions beginning with 8
		case 0x8000:
		{
			// Some Opcodes are differentiated by the last digit
			int mask = (OP & 0xF);

			//System.out.println(String.format("DBG: 8000: Mask: %x", mask));
			
			switch(mask) {
			case 0x0:
			{
				/*
				 * 8xy0 - LD Vx, Vy
				 * Set Vx = Vy.
				 */
				
				//System.out.println(String.format("8000: Vx: %x Vy: %x", V[x], V[y]));
				V[x] = V[y];

				break;
			}
			
			case 0x1:
			{
				/*
				 * 8xy1 - OR Vx, Vy
				 * Set Vx = Vx OR Vy.
				 */
				
				V[x] = (byte)(V[x] | V[y]);
				
				break;
			}
			
			case 0x2:
			{
				/*
				 * 8xy2 - AND Vx, Vy
				 * Set Vx = Vx AND Vy.
				 */
				
				//System.out.println(String.format("8003b: Vx: %x Vy: %x", V[x], V[y]));
				V[x] = (byte)(V[x] & V[y]);
				//System.out.println(String.format("8003a: Vx: %x Vy: %x", V[x], V[y]));
				
				break;
			}
			
			case 0x3:
			{
				/*
				 * 8xy3 - XOR Vx, Vy
				 * Set Vx = Vx XOR Vy.
				 */
				
				V[x] ^= V[y];
				
				break;
			}
			
			case 0x4:
			{
				/*
				 * 8xy4 - ADD Vx, Vy
				 * Set Vx = Vx + Vy, set VF = carry.
				 */
				
				V[x] = (byte)(V[x] + V[y]);
				
				if((V[x] + V[y]) > 255)
					V[0xF] = 1;
				else
					V[0xF] = 0;
				
				break;
			}
			
			case 0x5:
			{
				/*
				 * 8xy5 - SUB Vx, Vy
				 * Set Vx = Vx - Vy, set VF = NOT borrow.
				 */
					
				if(V[x] > V[y])
					V[0xF] = 1;
				else
					V[0xF] = 0;
				
				V[x] = (byte)(V[x] - V[y]);
				
				break;
			}
			
			case 0x6:
			{
				/*
				 * 8xy6 - SHR Vx {, Vy}
				 * Set Vx = Vx SHR 1.
				 */

				//System.out.println(String.format("DBG8006: V[x]: %x", V[x]));

				// Set carry flag if LSb of Vx is set
				if((V[x] & 0x1) == 1) {
					//System.out.println("DBG8006: Set");
					V[0xF] = 1;
				}
				else {
					//System.out.println("DBG8006: Not set");
					V[0xF] = 0;
				}
				
				V[x] >>= 1;
				
				break;
			}
			
			case 0x7:
			{
				/*
				 * 8xy7 - SUBN Vx, Vy
				 * Set Vx = Vy - Vx, set VF = NOT borrow.
				 */
				
				V[0xF] = (V[y] > V[x]) ? (byte)1 : 0;
				
				V[x] = (byte)(V[y] - V[x]);
				
				break;
			}
			
			case 0xE:
			{
				/*
				 * 8xyE - SHL Vx {, Vy}
				 * Set Vx = Vx SHL 1.
				 */

				//System.out.println(String.format("800E: V[x]&0x80: %x", ((V[x] & 0x80)>>7)));

				// Set flag register if MSb of Vx is set
				V[0xF] = (((V[x] & 0x80) >> 7) == 1) ? (byte)1 : 0;
				
				V[x] <<= 1;
				
				break;
			}
			
			// End masking
			}
			
		PC += 2;
		break;
		// End 0x8XXX
		}
		
		// Instructions beginning with 9
		case 0x9000:
		{
			/*
			 * 9xy0 - SNE Vx, Vy
			 * Skip next instruction if Vx != Vy.
			 */
			//System.out.println(String.format("Vx: %x; Vy: %x", V[x], V[y]));
			
			if(V[x] == V[y]) {
				//System.out.println("Not skipped");
				PC += 2;
			}
			else {
				//System.out.println("Skipped");
				PC += 4;
			}
			
			break;
		}
		
		// Instructions beginning with A
		case 0xA000:
		{
			/*
			 * Annn - LD I, addr
			 * Set I = nnn.
			 */
			
			//System.out.println("0xA: nnn: " +
			//	String.format("%x", nnn));

			//System.out.println(String.format("I: %x", I));
			I = (short)nnn;
			//System.out.println(String.format("I: %x", I));
			
			PC += 2;
			break;
		}
		
		// Instructions beginning with B
		case 0xB000:
		{
			/*
			 * Bnnn - JP V0, addr
			 * Jump to location nnn + V0.
			 */
			
			PC = (short)(nnn + V[0]);
			break;
		}
		
		// Instructions beginning with C
		case 0xC000:
		{
			/*
			 * Cxkk - RND Vx, byte
			 * Set Vx = random byte AND kk.
			 */
			
			V[x] = (byte)(gen.nextInt(255) & kk);
			
			PC += 2;
			break;
		}
		
		// Instructions beginning with D
		case 0xD000:
		{
			/*
			 * Dxyn - DRW Vx, Vy, nibble
			 * Display n-byte sprite starting at memory location I at (Vx, Vy),
			 * set VF = collision.
			 */

			// Number of bytes in the sprite (vertically)
			int n = (OP & 0xF);

			// Collision flag
			V[0xF] = 0;

			// X+Y locations are in the V registers
			int xLocation = (V[x] & 0xFF);
			int yLocation = (V[y] & 0xFF);
			//System.out.println(String.format("X:%x Y:%x", xLocation, yLocation));
			//System.out.println(String.format("VX:%x VY:%x", V[x], V[y]));

			// Uncomment these String debug's if you want sprites
			// written to console.
			//String debug = "";
			for(int lineY = 0; lineY < n; lineY++)
			{
				//debug = "";
				//System.out.println("pixel: I: " + I + " lY: " + lineY);
				int pixel = memory[I + lineY];
				//System.out.println(String.format("Pixel: %x; %x", memory[I + lineY], (I+lineY)));
				for(int lineX = 0; lineX < 8; lineX++)
				{
					// If this pixel is off, skip
					if((pixel & (0x80 >> lineX)) != 0)
					{
						if((xLocation + lineX) > 63) {
							//System.out.println("GFX: Skip Horizontal");
							continue;
						}
						if((yLocation + lineY) > 31) {
							//System.out.println("GFX: Skip Vertical");
							continue;
						}

						/*System.out.println(String.format("GFXDBG: x+lx: %x; y+ly: %x",
							(xLocation + lineX), (yLocation + lineY)));*/

						if(graphics[(xLocation + lineX)][(yLocation + lineY)] == 1)
							V[0xF] = 1;
						graphics[(xLocation + lineX)][(yLocation + lineY)] ^= 1;

						//debug += "*";
					}

					//debug += " ";
				}

				//System.out.println("GFXDBG: " + debug);
			}

			PC += 2;
			break;
		}
		
		// Instructions beginning with E
		case 0xE000:
		{
			// 0xE instructions are differentiated by the
			// least significant byte
			short mask = (short)(OP & 0x00FF);
			
			if(mask == 0x9E) {
				/*
				 * Ex9E - SKP Vx
				 * Skip next instruction if key with the value of Vx
				 * is pressed.
				 */

				System.out.println("Key input #1");
				
				key = input.getInput();
				
				//System.out.println(String.format("Key#1: %x", key));

				if(V[x] == key) {
					PC += 4;
					//System.out.println("Skipped");
				}
				else {
					PC += 2;
					//System.out.println("Not skipped");
				}
			}
			else if(mask == 0xA1) {
				/*
				 * ExA1 - SKNP Vx
				 * Skip next instruction if key with the value of Vx is
				 * not pressed.
				 */
				
				System.out.println("Key input #2");
				
				key = input.getInput();

				//System.out.println(String.format("Key#2: %x, x: %x", key, V[x]));
				//System.out.println("k: " + key + "x: " + x);

				if(V[x] != key) {
					PC += 4;
					//System.out.println("Skipped");
				}
				else {
					PC += 2;
					//System.out.println("Not skipped");
				}
			}
			
			//PC += 2;
			break;
		}
		
		// Instructions beginning with F
		case 0xF000:
		{
			// 0xF instructions differentiated by least
			// significant byte
			short mask = (short)(OP & 0x00FF);
			switch(mask) {
			
			case 0x07:
			{
				/*
				 * Fx07 - LD Vx, DT
				 * Set Vx = delay timer value.
				 */
				
				V[x] = (byte)delay_timer;
				
				break;
			}
			
			case 0x0A:
			{
				/*
				 * Fx0A - LD Vx, K
				 * Wait for a key press, store the value of the key in Vx.
				 */
			
				System.out.println("Key input #3");

				do {
					key = input.getInput();
					
					// Delay; Without this, oddities happen
					// TL;DR inifinite loop causes VM to not get input
					try {
						Thread.sleep(10);
					} catch(InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				} while(key == -1);

				V[x] = key;

				// Better alternative method: simply repeat
				// this instruction, caused bugs, TODO fix
				/*key = input.getInput();

				if(key == -1)
					PC -= 2;*/
				
				break;
			}
			
			case 0x15:
			{
				/*
				 * Fx15 - LD DT, Vx
				 * Set delay timer = Vx.
				 */
				
				delay_timer = (V[x] & 0xFF);
				
				break;
			}
			
			case 0x18:
			{
				/*
				 * Fx18 - LD ST, Vx
				 * Set sound timer = Vx.
				 */
				
				sound_timer = (V[x] & 0xFF);
				
				break;
			}
			
			case 0x1E:
			{
				/*
				 * Fx1E - ADD I, Vx
				 * Set I = I + Vx.
				 */
				
				//System.out.println(String.format("1E: Vx: %x", V[x]));
				I = (short)(I + V[x]);
				
				break;
			}
			
			case 0x29:
			{
				/*
				 * Fx29 - LD F, Vx
				 * Set I = location of sprite(font) for digit Vx.
				 */

				// Each font sprite is held in memory starting
				// at 0 and is 5 bytes wide.
				I = (short)(V[x] * 5);
				
				break;
			}
			
			case 0x33:
			{
				/*
				 * Fx33 - LD B, Vx
				 * Store BCD representation of Vx in memory locations I,
				 * I+1, and I+2.
				 */

				// Convert byte to a BCD-like character array
				// The AND is to cast the byte into an unsigned state
				char temp[] = String.valueOf((int)(V[x] & 0xFF)).toCharArray();

				// Not all values will convert to 3 BCD digits, so
				// we need to set the others to zero
				// The values go in order of: hundreds, tens, ones
				char BCD[] = {0, 0, 0};

				// Replace the zero'd BCD array with proper values, if applicable
				for(int place = 0, count = 2; place < temp.length; place++, count--)
				{
					BCD[count] = temp[place];
				}

				// Finally place the BCD values in I, I+1, I+2
				for(int count = 0; count < 3; count++)
				{
					// If the value is zero, just place it in
					if(BCD[count] == 0)
					{
						memory[I + count] = 0;
					}
					else
					{
						// This doesn't seem to like it when the BCD is 0
						memory[I + count] =
							(byte)Character.getNumericValue(BCD[count]);
					}
					//System.out.print("FX33 " + memory[I + count] + " ");
				}

				break;
			}
			
			case 0x55:
			{
				/*
				 * Fx55 - LD [I], Vx
				 * Store registers V0 through Vx in memory starting at
				 * location I.
				 */
				
				for(int counter = 0; counter <= x; counter++)
					memory[I + counter] = V[counter];
				
				break;
			}
			
			case 0x65:
			{
				/*
				 * Fx65 - LD Vx, [I]
				 * Read registers V0 through Vx from memory starting at
				 * location I.
				 */

				for(int counter = 0; counter <= x; counter++) {
					System.out.println(String.format("DBG65: %x", memory[(I+counter)]));
					V[counter] = memory[I + counter];
				}

				break;
			}
			
			// End mask
			}
			
		PC += 2;
		break;
		// End 0xF---
		}
		
		} // End main switch
	} // End Execute()
} // End EmulatorCore
