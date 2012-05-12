import java.util.*;
import java.io.*;

import javax.sound.midi.*;

public class MIDIWorkshop {

	PrintWriter out;
	File data;
	private final int HOLD = 128;
	private final int REST = 129;

	// Outputs the MIDI form of the MusicSelection @source at the location @destination
	public MIDIWorkshop(MusicSelection source, String songName, String destination) throws InvalidMidiDataException, IOException {
		data = new File(destination);
		out = new PrintWriter(new FileWriter(data));
		out.println("MFile 1 2 96");
		out.println("MTrk");
		out.println("0 TimeSig 4/4 24 8");
		out.println("0 KeySig 0 major");
		out.println("0 Tempo 600000");
		out.println("0 Meta TrkName \""+ songName + "\"");
		out.println("1 Meta TrkEnd");
		out.println("TrkEnd");
		out.println("MTrk");
		out.println("0 Meta TrkName \"Piano\"");
		// 25 midi units per 16th note
		int MIDI_UNITS_PER_NOTE = 25 * 16 / source.getChunkSize();
		int count = MIDI_UNITS_PER_NOTE;
		ArrayList<Integer> chunks = source.getChunks();
		int i = 0;
		while (i < chunks.size()) {
			int curr = chunks.get(i);
			if (isNote(curr)) {
				out.println(count+" On ch=1 n="+curr+" v=70");
				do {
					i++;
					count += MIDI_UNITS_PER_NOTE;
				}
				while(i<chunks.size() && chunks.get(i)==HOLD);
				out.println(count+" Off ch=1 n="+curr+" v=70");
			}
			else if (curr == REST) {
				do {
					i++;
					count += MIDI_UNITS_PER_NOTE;
				}
				while(i<chunks.size() && chunks.get(i)==HOLD);
			}
		}
		out.println((count+1)+" Meta TrkEnd");
		out.println("TrkEnd");
		out.close();
	}	

	public boolean isNote(int n) {return (n>=0 && n<HOLD);}
}
