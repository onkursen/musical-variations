import java.util.*;
import java.io.*;

import javax.sound.midi.InvalidMidiDataException;

public class Driver {
	public static void main (String args []) throws IOException, InvalidMidiDataException {	
		long startTime = System.currentTimeMillis();
		int numSteps = 100;
		ArrayList<MusicSelection> pieces = new ArrayList<MusicSelection>();
		Scanner files = new Scanner(new File("files.txt"));
		
		while (files.hasNext()) { // adds a new piece from each filename
			String filename = files.nextLine();
			Scanner in = new Scanner(new File(filename));
			ArrayList<Integer> notes = new ArrayList<Integer>();
			int chunkSize = in.nextInt();
			while (in.hasNext())
				notes.add(in.nextInt());
			
			pieces.add(new MusicSelection(notes, chunkSize));
		}
		
//		MIDIWorkshop w = new MIDIWorkshop(pieces.get(0), "invention01.txt");
//		System.exit(0);
		
		MusicGeneticAlgorithm g = new MusicGeneticAlgorithm(pieces);
//		System.out.println(g.toNotes(pieces.get(0)));
//		System.exit(0);
		
		g.runAlgorithm(numSteps);
		MusicSelection generated = g.getBestPiece();
		System.out.println("Best result:\n" +generated);
		System.out.println(g.fitnessOrig(generated)+", "+g.fitness(generated));
		System.out.println(g.toNotes(generated));
//		MIDIWorkshop ws = new MIDIWorkshop(generated, "Invention No. 1", "variant.txt");
		generated.generateMIDIText("Invention No. 1", "variant.txt");

		System.out.println("total time taken: " + (System.currentTimeMillis() - startTime) + " milliseconds");
	}
}
