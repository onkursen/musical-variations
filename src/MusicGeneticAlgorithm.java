import java.util.*;
import java.io.*;

/*
 * Separate absolute and relative representations (one at a time via flag)
 * Constraint on values of relative representation
 * Multiple sources without niching
 */

class MusicGeneticAlgorithm {
	// Variables
	private ArrayList<MusicSelection> population;
	private ArrayList<MusicSelection> origPieces;
	private Random gen;
	private int chunkSize;
	private int POPULATION_SIZE;

	// Constants
	// Flag for method of calculating fitness for multiple pieces: 0 = average, 1 = min, 2 = max
	private int FITNESS_CODE = 0; 
	private boolean fitnessFlag = false; // Niching
	private final int NUM_INDIVS_PER_ORIG = 50;
//	private final double originalPieceMutationRate = 0.05;
	private final int MUTATION_DISTANCE = 2; // 2 = mutation by whole step
	private final double NUM_POINTS_OF_CROSSOVER = 2;
	private final double PROBABILITY_OF_MUTATION = 0.05; // Probability of mutating each note
	private final double CROSSOVER_RATE = 0.9;
	private final double RANDOM_INITIALIZED = 0.5;
	private final double INITIAL_MUTATION_RATE = 0.33;
	private final int MAX_NOTE_VALUE = 129; // 0-127 for MIDI notes, 128 for hold, 129 for rest
	private final int HOLD = 128;
	private final int REST = 129;
	private final int NUM_NOTES_PER_OCTAVE = 12;
	private final double OPTIMAL_FITNESS = 0.95;

	/* Constructor:
	 * 1. Equalizes all original pieces such that chunk size is constant
	 * 2. Generates initial population of individuals of two types: completely random and mutated variants of originals 
	 */
	public MusicGeneticAlgorithm(ArrayList<MusicSelection> pieces) {		
		gen = new Random(123456);
		origPieces = pieces;
		POPULATION_SIZE = origPieces.size()*NUM_INDIVS_PER_ORIG;
		population = new ArrayList<MusicSelection>(POPULATION_SIZE);
		
//		Equalizes all original pieces such that chunk size is constant
		chunkSize = 0;
		for (MusicSelection s : origPieces)
			chunkSize = Math.max(chunkSize, s.getChunkSize());
		for (MusicSelection s : origPieces) {
			equalize(s, chunkSize);
			// Mutates original pieces by a certain rate in order to maintain certain distance from original
//			mutate(s, originalPieceMutationRate);
		}

//		Generates initial population of individuals of two types: completely random 
//		and mutated variants of originals
		int randomlyGenerated =  (int)(RANDOM_INITIALIZED * POPULATION_SIZE);
		for (int i = 0; i < randomlyGenerated; i++)	{
			population.add(new MusicSelection(getRandomIndividual(origPieces).length(), chunkSize));
		}
		for (int j = randomlyGenerated; j < POPULATION_SIZE; j++) {
			MusicSelection mutatedCopy = new MusicSelection(getRandomIndividual(origPieces));
//			System.out.println("Before mutating : "+pieces.get(index));
			mutate(mutatedCopy, INITIAL_MUTATION_RATE);
//			System.out.println("After mutating: "+pieces.get(index));
//			System.out.println("Mutated" +toBeMutated);
			population.add(mutatedCopy);
		}

	/*	int maxLength = 0;
		for (MusicSelection x : origPiecesAbsolute)
			maxLength = Math.max(maxLength, x.length());
		MusicSelection s = new MusicSelection(maxLength, chunkSize);
		for (int i = 0; i<maxLength; i++) {
			int total = 0;
			for (MusicSelection x : origPiecesAbsolute)
				if (i < x.length())
					total += x.getChunkAt(i);
			s.setChunkAt(i, total/origPiecesAbsolute.size());
		}
		System.out.println("Fitness of centroid: "+fitness(s));*/
	}

	/*
	 * Cosine similarity between two MusicSelections treated as vectors
	 * Special properties:
	 * 1. Hold operator is replaced with value of last note
	 * 2. Notes differing by an octave are considered to be equal.
	 */
	public double similarity(MusicSelection s1, MusicSelection s2) {
		double AdotB = 0;
		double magA = 0;
		double magB = 0;
		int minLength = Math.min(s1.length(), s2.length());
		int lastNoteA = REST;
		int lastNoteB = REST;
		for (int i = 0; i < minLength; i++) {
			int currA = s1.getChunkAt(i);
			int currB = s2.getChunkAt(i);
			if (isNote(currA))
				lastNoteA = currA % NUM_NOTES_PER_OCTAVE;
			if (isNote(currB))
				lastNoteB = currB % NUM_NOTES_PER_OCTAVE;

			AdotB += lastNoteA * lastNoteB;
			magA += Math.pow(lastNoteA, 2);
			magB += Math.pow(lastNoteB, 2);
		}
		return AdotB / Math.sqrt(magA*magB);
	}

	/* Cosine similarity among original pieces. 
	 * Fitness code corresponds to different procedure of determining overall fitness.
	 * FITNESS_CODE = 0: Average
	 * FITNESS_CODE = 1: Minimum
	 * FITNESS_CODE = 2: Maximum
	 */
	public double fitnessOrig(MusicSelection transposed) {
		ArrayList<Double> fitnesses = new ArrayList<Double>(origPieces.size());
		int tLength = transposed.length();
		
		for (MusicSelection orig : origPieces) {
			MusicSelection test = new MusicSelection(transposed);
			int firstOrig = 0;
			while (orig.getChunkAt(firstOrig) == REST || orig.getChunkAt(firstOrig) == HOLD)
				firstOrig++;
			int firstTest = 0;
			while (orig.getChunkAt(firstTest) == REST || orig.getChunkAt(firstTest) == HOLD)
				firstTest++;
			int difference = orig.getChunkAt(firstOrig)-test.getChunkAt(firstTest);
			for (int i = firstTest; i < tLength; i++) {
				int currChunk = test.getChunkAt(i);
				if (currChunk != HOLD && currChunk != REST)
					test.setChunkAt(i, currChunk-difference);
			}
			fitnesses.add(similarity(orig, test));
		}

		if (FITNESS_CODE == 0) { // average
			double total = 0;
			for (double x : fitnesses)
				total += x;
			return total / fitnesses.size();
		}
		else if (FITNESS_CODE == 1) // min
			return Collections.min(fitnesses);
		else // max
			return Collections.max(fitnesses);
	}

	// Returns shared fitness
	public double fitness(MusicSelection s) {
		if (fitnessFlag) {
			double totalShared = 0;
			for (MusicSelection x : population)
				totalShared += similarity(s,x);
			return (1 - Math.abs(fitnessOrig(s) - OPTIMAL_FITNESS))/totalShared;
		}
		return 1 - Math.abs(fitnessOrig(s) - OPTIMAL_FITNESS);
	}

	// Randomly generates bitmask with fixed number of points of crossover
	public int[] makeBitmask(MusicSelection s1, MusicSelection s2) {
		int l1 = s1.length();
		int l2 = s2.length();
		int minLength = Math.min(l1, l2);
		int maxLength = Math.max(l1, l2);

		int[] bitmask = new int[maxLength];
		int loc;
		// Labels distinct points of crossover with 1's in an array initially consisting of all 0's
		for (int i = 0; i < NUM_POINTS_OF_CROSSOVER; i++) {
			do loc = gen.nextInt(minLength); while (bitmask[loc] == 1);
			bitmask[loc] = 1;
		}

		// Fills in gaps between 1's
		boolean change = false;
		for (int i = 0; i<minLength; i++) {
			if (!change && bitmask[i]==1) // Prev = 0, curr = 1
				change = true;
			else if (change && bitmask[i]==1) // Prev = 1, curr = 1
				change = false;
			else if (change) // Prev = 1, curr = 0
				bitmask[i] = 1;
		}
		// If second gene longer, fills rest of bitmask with 1's
		if (l2 > l1)
			Arrays.fill(bitmask, minLength, maxLength, 1);
		return bitmask;
	}

	/* Crosses over two individuals using random bitmask generated by makeBitmask().
	 * Returns the offspring from the crossover.
	 */
	public ArrayList<MusicSelection> crossover(MusicSelection s1, MusicSelection s2){
		int[] bitmask = makeBitmask(s1, s2);
		int bLength = bitmask.length;
		//		System.out.println("Bitmask being used:");
		//		System.out.println(Arrays.toString(bitmask));
		
		// Templates for offspring 
		ArrayList<Integer> new1 = new ArrayList<Integer>();
		ArrayList<Integer> new2 = new ArrayList<Integer>();

		int crossoverStopIndex = Math.min(s1.length(), s2.length());
		
		// Values in bitmask determine assignment to offspring 
		for (int i = 0; i < crossoverStopIndex; i++) {
			if (bitmask[i] == 0) {
				new1.add(s1.getChunkAt(i));
				new2.add(s2.getChunkAt(i));
			}
			else {
				new1.add(s2.getChunkAt(i));
				new2.add(s1.getChunkAt(i));
			}
		}
		
		// Appends rest of longer parent to first offspring
		if (bitmask[bLength-1] == 0)
			for (int j = crossoverStopIndex; j < bLength; j++)
				new1.add(s1.getChunkAt(j));
		else
			for (int j = crossoverStopIndex; j < bLength; j++)
				new1.add(s2.getChunkAt(j));

		ArrayList<MusicSelection> children = new ArrayList<MusicSelection>(2);
		children.add(new MusicSelection(new1, chunkSize));
		children.add(new MusicSelection(new2, chunkSize));
		return children;
	}

	/* Randomly mutates each chunk with a certain probability.
	 * If the chunk is a note, it is mutated up or down with equal probability.
	 * If the chunk is a rest/hold, it is mutated to a hold/rest or a note with equal probability.
	 * If the mutation results in an invalid value for the chunk, it is reset to a random valid value.
	 */
	public void mutate(MusicSelection s, double prob) {
		int sLength = s.length();
		for (int i = 0; i < sLength; i++) {
			int currChunk = s.getChunkAt(i);
			if (isNote(currChunk) && gen.nextDouble() < prob) { // chunk is a note
				mutateFromNote(s, i);
			}
			else if (gen.nextDouble() < prob) { // Chunk is a hold or rest
				if (gen.nextDouble() > 0.5) { // mutate to rest/hold
					// If hold mutated to rest, and next location is a hold,
					// next location is changed to the last note value to prevent excessive
					// loss of music
					if (currChunk == HOLD) {
						if (s.getChunkAt(i+1) == HOLD)
							s.setChunkAt(i+1, s.lastNote(i));
						s.setChunkAt(i, REST);
					}
					// If rest mutated to hold, and next location is a hold,
					// next location is changed to a rest to prevent excessive adding of music
					if (currChunk == REST && i > 0) {
						if (s.getChunkAt(i+1) == HOLD)
							s.setChunkAt(i+1, REST);
						s.setChunkAt(i, HOLD);
					}
				}
				else if (i > 0) // mutate to note if piece does not start with rest
					mutateFromNote(s, i);
			}
		}
	}

	public void mutateFromNote(MusicSelection s, int position) {
		int lastNote = s.lastNote(position);
		int updated = -1;
		if (gen.nextDouble() > 0.5) 
			updated = lastNote+1+gen.nextInt(MUTATION_DISTANCE);
		else 
			updated = lastNote-1-gen.nextInt(MUTATION_DISTANCE);
		if (updated < 0 || updated > MAX_NOTE_VALUE)
			updated = gen.nextInt(MAX_NOTE_VALUE-1);
		s.setChunkAt(position, updated);
	}

	//ATTENTION: Start reviewing from here
	// Returns piece with the highest fitness
	public MusicSelection getBestPiece() {
		MusicSelection best = population.get(0);
		for (int i = 1; i < population.size(); i++)
			best = better(best, population.get(i));
		return best;
	}

	/* Generates a new generation of individuals each step for a number of steps.
	 * Incorporates elitism, carrying the 2 fittest individuals from one generation to the next.
	 * Also uses tournament selection, selecting two random individuals twice and choosing the fitter ones as parents for crossover.
	 * After crossing over, mutates the offspring and adds to the next generation.
	 */
	public void runAlgorithm(int numSteps) throws IOException {
		for (int i = 0; i < numSteps; i++) {
			// Print population info to files
			/*if (i % 10 == 0) {
				printPopulationInfo(i);
			}*/
			
			ArrayList<MusicSelection> newPopulation = new ArrayList<MusicSelection>(POPULATION_SIZE);

			// Elitism: adds 2 fittest individuals immediately to next generation
			MusicSelection best = getBestPiece();
			newPopulation.add(best);
			population.remove(best);
			newPopulation.add(getBestPiece());
			population.add(best);

			// Tournament selection
			for (int j = 2; j<POPULATION_SIZE; j+=2) {
				MusicSelection s1 = new MusicSelection(better(getRandomIndividual(population),getRandomIndividual(population)));
				MusicSelection s2 = new MusicSelection(better(getRandomIndividual(population),getRandomIndividual(population)));
				if (gen.nextDouble() < CROSSOVER_RATE) {
					ArrayList<MusicSelection> afterCrossover = crossover(s1, s2);
					s1 = afterCrossover.get(0);
					s2 = afterCrossover.get(1);
				}
				mutate(s1, PROBABILITY_OF_MUTATION);
				mutate(s2, PROBABILITY_OF_MUTATION);
				s1.check();
				s2.check();
				newPopulation.add(s1);
				newPopulation.add(s2);
			}
			population = new ArrayList<MusicSelection>(newPopulation);
		}
//		printInfo();
//		printPopulationInfo(numSteps);
	}

	/* Inserts holds after every note in a piece such that desired chunk size is achieved. 
	 * It is assumed that the desired chunk size is smaller (i.e., the actual number is larger) than the original chunk size.
	 * Thus, holds are added; notes are not removed.
	 */
	public void equalize(MusicSelection s, int targetChunkSize) {
		int ratio = targetChunkSize / s.getChunkSize() ;
		for (int i = 0; i < s.length(); i++)
			for (int j = 0; j<ratio-1; j++) // add ratio-1 holds per note (1 note in shorter = ratio notes in longer)
				s.addHold(i*ratio+1);
		s.setChunkSize(targetChunkSize);
	}


	// Returns the selection with higher fitness
	public MusicSelection better(MusicSelection s1, MusicSelection s2) {
		if (fitness(s1) >= fitness(s2))
			return s1;
		return s2;
	}

	// Prints the contents and fitness of each individual in the population
	public void printInfo() {
		for (MusicSelection x : population) {
			System.out.println(x);
			System.out.println(fitnessOrig(x)+", "+fitness(x));
		}
	}

	public boolean isNote(int n) {return (n>=0 && n<HOLD);}

	public MusicSelection getRandomIndividual(ArrayList<MusicSelection> a) {
		return a.get(gen.nextInt(a.size()));
	}

	// converts to note representation
	public String toNotes(MusicSelection s) {
		String translation = "";
		for (int i = 0; i < s.length(); i++) {
			int currChunk = s.getChunkAt(i);
//			translation += currChunk + " ";
			if (currChunk == HOLD)
				translation += "h, ";
			else if (currChunk == REST)
				translation += "r, ";
			else {
				int mod = currChunk % NUM_NOTES_PER_OCTAVE;
				switch (mod) {
				case 0: translation += "c"; break;
				case 1: translation += "c#"; break;
				case 2: translation += "d"; break;
				case 3: translation += "d#"; break;
				case 4: translation += "e"; break;
				case 5: translation += "f"; break;
				case 6: translation += "f#"; break;
				case 7: translation += "g"; break;
				case 8: translation += "g#"; break;
				case 9: translation += "a"; break;
				case 10: translation += "a#"; break;
				case 11: translation += "b"; break;
				}
				translation += (currChunk-mod)/12;
				translation +=", ";
			}
		}
		return translation;
	}

	public void printPopulationInfo(int n) throws IOException {
		int maxLength = 0;
		for (MusicSelection s : population)
			maxLength = Math.max(maxLength, s.length());
		PrintWriter out = new PrintWriter(new FileWriter("population"+n+".txt"));
		for (int j = 0; j < maxLength; j++) {
			out.printf("%d ", j);
			for (MusicSelection s : population) {
				if (s.length() > j)
					out.printf("%3d ", s.getChunkAt(j));
				else
					out.printf("%3d ", 0);
			}
			out.println();
		}
		out.close();
	}
}
