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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;

/*
	This is the class that implements our graphics driver.

	The significance of this class is that it effectively makes
	the interpreter core completely modular and insulated from
	the graphics engine. In this particular example, Java's primitive
	drawing is used as the graphics engine. By changing this class,
	one can use SDL, OpenGL, etc. by just retrieving the video RAM.
*/
public class Video extends JComponent
{
	Color color, background;
	protected byte[][] VRam;

	public Video(byte[][] VideoRAM)
	{
		VRam = VideoRAM;
		// Setup colors, background white, sprites black
		color = Color.darkGray;
		background = Color.BLACK;
	}

	public void paintComponent(Graphics G)
	{
		Graphics2D g = (Graphics2D) G;
		g.setColor(color);

		Rectangle2D rect;

		// x and y are for our 4x resolution drawing (onto Java frame)
		// xOriginal and yOriginal are for getting the pixel from
		// the original VRAM. This is relatively ugly.
		for(int x = 0, xOriginal = 0; x < 256; x += 4, xOriginal++)
		{
			for(int y = 0, yOriginal = 0; y < 128; y += 4, yOriginal++)
			{
				// If this pixel is set(on), draw a 4*4 rectangle
				if(VRam[xOriginal][yOriginal] == 1) {
					// Setup rectangle
					rect = new Rectangle2D.Double((double)x,
						(double)y, (double)4, (double)4);

					// The rectangle will be hollow without this
					g.fill(rect);

					// Draw to the amazement of all
					g.draw(rect);
				}
			}
		}

	}
}
