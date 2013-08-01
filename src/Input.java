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
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

/*
	Input class.

	Listen for all keystrokes, if they're legal for Chip8, store the
	last valid key and return it on request.
*/
public class Input {
	JFrame frame;
	private char key;
	private byte hexKey = -1;

	public Input(JFrame jFrame) {
		frame = jFrame;

		frame.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent e) {
				key = e.getKeyChar();

				//System.out.println("keyDBG: " + key);

				/* 
					Chip-8 Keyboard layout:
					(1234)
					123C
					(QWER)
					456D
					(ASDF)
					789E
					(ZXCV)
					A0BF

					Probably a better way to do this?
				*/
				switch(Character.toLowerCase(key)) {
				case '1':
					hexKey = 0x01;
					break;
				case '2':
					hexKey = 0x02;
					break;
				case '3':
					hexKey = 0x03;
					break;
				case '4':
					hexKey = 0x0C;
					break;
				case 'q':
					hexKey = 0x04;
					break;
				case 'w':
					hexKey = 0x05;
					break;
				case 'e':
					hexKey = 0x06;
					break;
				case 'r':
					hexKey = 0x0D;
					break;
				case 'a':
					hexKey = 0x07;
					break;
				case 's':
					hexKey = 0x08;
					break;
				case 'd':
					hexKey = 0x09;
					break;
				case 'f':
					hexKey = 0x0E;
					break;
				case 'z':
					hexKey = 0x0A;
					break;
				case 'x':
					hexKey = 0x00;
					break;
				case 'c':
					hexKey = 0x0B;
					break;
				case 'v':
					hexKey = 0x0F;
					break;
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyTyped(KeyEvent e) {
			}
		});
	}

	/*
		Return last valid input or -1 on error/none.

		Clear the last inputed key afterword.
	*/
	public byte getInput()
	{
		/*
			Clear key after getting got. Used for op 0xFX0A (wait
				for key pressed and store in X)
		*/
		byte temp = hexKey;
		hexKey = -1;

		return temp;
	}
}
