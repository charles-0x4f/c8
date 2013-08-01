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
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;

/*
	Sound class.

	Responsible for making sound.
*/
public class Sound implements Runnable {
	File file;
	AudioInputStream audioStream;
	AudioFormat format;
	DataLine.Info info;
	Clip clip;
	boolean on;

	/*
		Initialize the sound subsystem
	*/
	public Sound() {
		try {
			file = new File("tone.wav");
			audioStream = AudioSystem.getAudioInputStream(file);
			format = audioStream.getFormat();
			info = new DataLine.Info(Clip.class, format);
			clip = (Clip)AudioSystem.getLine(info);
			clip.open(audioStream);
			on = false;
		} catch(UnsupportedAudioFileException e) {
			System.out.println("SOUND FAILURE");
			e.printStackTrace();
		} catch(LineUnavailableException e) {
			System.out.println("SOUND FAILURE");
			e.printStackTrace();
		} catch(IOException e) {
			System.out.println("SOUND FAILURE");
			e.printStackTrace();
		}
	}

	/*
		Our sound file is only a second long or so and we need to
		constantly be ready to play/repeat it.

		Threading is a solution to this.
	*/
	public void run() {
		do {
			// If we're supposed to be playing sound
			if(on) {
				// If the sound clip isn't running, run it
				// forever
				if(!clip.isRunning()) {
					clip.loop(Clip.LOOP_CONTINUOUSLY);
				}
			}
			// If we're not supposed to be playing sound
			else {
				// If the sound clip is running, turn it off
				if(clip.isRunning()) {
					clip.stop();
				}
			}

			// This is so the Java VM can do other things than
			// sound pool
			try {
				Thread.sleep(100);
			} catch(InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		} while(true);
	}

	/*
		Method to turn sound on or off.
	*/
	public void setPlaying(boolean trigger) {
		if(trigger)
			on = true;
		else
			on = false;
	}

	/*
		Method to find out whether the sound is currently playing
	*/
	public boolean isPlaying() {
		if(on)
			return true;

		return false;
	}
}
