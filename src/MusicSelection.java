import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import javax.sound.midi.InvalidMidiDataException;

public class MusicSelection {
	private ArrayList<Integer> chunks; // vector with note, rest, and hold values
	private int length;
	private Random gen;
	private final int MAX_NOTE_VALUE = 129; // 0-127 for MIDI notes, 128 for hold, 129 for rest
	private final int HOLD = 128;
	private final int REST = 129;
	private int chunkSize; 
	private int SELECTION_TYPE = 0; // 0 = absolute, 1 = relative
	private final int MAX_INTERVAL = 8; 

	// Constructs a random MusicSelection of length l and chunk size cs.
	public MusicSelection(int l, int cs) {
		chunkSize = cs;
		length = l;
		chunks = new ArrayList<Integer>();
		gen = new Random(123456789);
		// First chunk cannot be hold
		int first;
		do {
			first = gen.nextInt(MAX_NOTE_VALUE+1);
		} while(first == HOLD);
		chunks.add(first);
		
		// If absolute model, generates pieces with random values between 0 and 129
		if (SELECTION_TYPE == 0) {
			for (int i = 1; i<length; i++) 
				chunks.add(gen.nextInt(MAX_NOTE_VALUE+1));
		}
		// If relative model, generates pieces with random values in [-MAX_INTERVAL, MAX_INTERVAL]
		else if (SELECTION_TYPE == 1) {
			for (int i = 1; i<length; i++) {
				int val = gen.nextInt(MAX_INTERVAL);
				if (gen.nextDouble() > 0.5)
					val *= -1;
				chunks.add(val);
			}
		}
	}

	// Generates a MusicSelection from an ArrayList vector of values and a chunk size.
	public MusicSelection(ArrayList<Integer> list, int cs) {
		chunkSize = cs;
		chunks = list;
		length = list.size();
	}

	// Copy constructor
	public MusicSelection(MusicSelection orig) {
		chunks = orig.getChunks();
		length = orig.length();
		chunkSize = orig.getChunkSize();
	}

	public String toString() {
		if (SELECTION_TYPE == 0)
			return chunks.toString();
		return toAbsolute().toString();
	}

	public int length() {return length;}
	public int getChunkSize() {return chunkSize;}
	public void setChunkSize(int c) {chunkSize = c;}
	public ArrayList<Integer> getChunks() {return chunks;}
	public int getChunkAt(int index) {return chunks.get(index);}
	public void addHold(int index) {chunks.add(index, HOLD);}
	public boolean isNote(int n) {return (n>=0 && n<HOLD);}
	
	// Returns position of last note before location at index.
	public int lastNote(int index) {
		for (int j = index-1; j>=0; j--) {
			int curr = chunks.get(j);
			if (isNote(curr))
				return curr;
		}
		return REST;
	}

	/*
	 * Changes value at a certain location
	 * Absolute model: changes value at location.
	 * Relative model: replaces location at @index with @newValue, replaces value at @index+1
	 * with difference between that value and previous value at @index
	 */
	public void setChunkAt(int index, int newValue) {
		if (SELECTION_TYPE == 0)
			chunks.set(index, newValue);
		else if (SELECTION_TYPE == 1) {
			chunks.set(index, newValue);
			if (index < length()-1) {
				int diffAtIndex = (newValue-chunks.get(index));
				chunks.set(index+1, chunks.get(index+1)-diffAtIndex);
			}
		}
	}

	// Absolute only: converts to relative
	public void toRelative() {
		ArrayList<Integer> relative = new ArrayList<Integer>(chunks);
		int firstNoteLoc = 0;
		while(!isNote(relative.get(firstNoteLoc)))
			firstNoteLoc++;
		// Starts at end of piece and goes backward to preserve relative relationships
		for (int i = relative.size()-1; i > firstNoteLoc; i--) {
			int currChunk = relative.get(i);
			if (!isNote(currChunk))
				relative.set(i, currChunk);
			else {
				int prevNoteLoc = i-1;
				while(!isNote(relative.get(prevNoteLoc)))
					prevNoteLoc--;
				relative.set(i, currChunk-relative.get(prevNoteLoc));
			}
		}
	}
	
	// relative only: converts to absolute
	public ArrayList<Integer> toAbsolute() {
		ArrayList<Integer> abs = new ArrayList<Integer>();
		int i = 0;
		// Add all rests and holds at beginning of piece
		for (; chunks.get(i) == REST || chunks.get(i) == HOLD; i++)
			abs.add(chunks.get(i));
		// Adds first note value
		abs.add(chunks.get(i));
		i++;
		// If rests or holds, add as is. 
		// otherwise, adds previous note value + difference (value at current index)
		for (; i < length; i++) {
			int curr = chunks.get(i);
			if (curr == HOLD || curr == REST)
				abs.add(curr);
			else
				abs.add(lastNote(i)+curr);
		}
		return abs;
	}
	
	// relative only; checks if all values are in the range [-1*MAX_INTERVAL, MAX_INTERVAL]
	public void check() {
		if (SELECTION_TYPE == 1) {
			for (int x : chunks) {
				if (x < -1*MAX_INTERVAL)
					x = -1*MAX_INTERVAL;
				else if (x > MAX_INTERVAL)
					x = MAX_INTERVAL;
			}
		}
	}
	
	public void generateMIDIText(String songName, String destination) 
		throws InvalidMidiDataException, IOException {
		PrintWriter out = new PrintWriter(new FileWriter(new File(destination)));
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
		int MIDI_UNITS_PER_NOTE = 25 * 16 / getChunkSize();
		int count = MIDI_UNITS_PER_NOTE;
		int i = 0;
		while (i < length) {
			int curr = chunks.get(i);
			if (isNote(curr)) {
				out.println(count+" On ch=1 n="+curr+" v=70");
				do {
					i++;
					count += MIDI_UNITS_PER_NOTE;
				}
				while(i<length && chunks.get(i)==HOLD);
				out.println(count+" Off ch=1 n="+curr+" v=70");
			}
			else if (curr == REST) {
				do {
					i++;
					count += MIDI_UNITS_PER_NOTE;
				}
				while(i<length && chunks.get(i)==HOLD);
			}
		}
		out.println((count+1)+" Meta TrkEnd");
		out.println("TrkEnd");
		out.close();
		
//		System.out.println(MidiSystem.getMidiFileFormat(data));
//		Sequence s = MidiSystem.getSequence(data);
//		File output = new File("variant.mid");
//		MidiSystem.write(s, 0, output);		
	}
}
