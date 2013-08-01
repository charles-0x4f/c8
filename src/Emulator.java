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

import javax.swing.JFrame;

/*
	Main class

	Initialize subsystems and begin emulation cycle.
*/
public class Emulator {
	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setSize(275, 170);
		frame.setTitle("C8 - Chip 8 Interpreter");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// If no ROM was passed through command line
		if(args.length == 0)
		{
			System.out.println("No ROM");
			return;
		}

		// Set ROM filename from command line
		String ROM = args[0];

		// Create input and emulation core instance
		Input input = new Input(frame);
		EmulatorCore emulator = new EmulatorCore(ROM, input);

		// Initialize graphics
		Video video = new Video(emulator.getVRam());
		frame.add(video);
		frame.setVisible(true);

		// Emulation cycle
		do {
			emulator.cycle();
			frame.repaint();
		} while(true);
	}
}
