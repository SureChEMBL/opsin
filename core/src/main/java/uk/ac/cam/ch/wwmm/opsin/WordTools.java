package uk.ac.cam.ch.wwmm.opsin;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Tools for dealing uniformly with unusually-formed words.
 */
public class WordTools {

	private final static char endOfFunctionalTerm = '\u00FB';
	private final static char endOfSubstituent = '\u00e9';
	private final static char endOfMainGroup = '\u00e2';

	static List<ParseWord> splitIntoParseWords(List<ParseTokens> parseTokensList, String chemicalName) throws ParsingException {
		List<ParseTokens> wellFormedParseTokens = new ArrayList<ParseTokens>();//these are all in the same word as would be expected
		List<List<ParseTokens>> omittedWordParseTokensList = new ArrayList<List<ParseTokens>>();//these are grouped into words e.g. ethylchloride will have a list of parseTokens for the ethyl and chloride
		omittedWordParseTokensList.add(new ArrayList<ParseTokens>());
		omittedWordParseTokensList.add(new ArrayList<ParseTokens>());//only 1 space is allowed to be omitted
		int longestFunctionalTermEncountered = 0;//we want the longest functional term
		int shortestNonFunctionalTermEncountered = Integer.MAX_VALUE;//and the shortest non functional term
		for (ParseTokens parseTokens : parseTokensList) {
			List<Character> annotations = parseTokens.getAnnotations();
			List<List<Character>> chunkedAnnotations = chunkAnnotations(annotations);//chunked into mainGroup/substituent/functionalTerm
			if (chunkedAnnotations.size() > 1 && annotations.contains(endOfFunctionalTerm)) {//must be an omitted space as not allowed to have a functionalTerm and anything else
				List<String> tokens = parseTokens.getTokens();
				List<Character> newAnnotations = new ArrayList<Character>();
				List<String> newTokens = new ArrayList<String>();
				int annotPos = 0;
				int wordCounter = 0;
				for (List<Character> annotationList : chunkedAnnotations) {
					boolean functionalTermNext = false;
					if (annotationList.get(annotationList.size() - 1).equals(endOfFunctionalTerm)) {
						functionalTermNext = true;
						if (newAnnotations.size() > 0) {//create a new parseTokens, unless nothing has been read yet e.g. in the case of poly
							ParseTokens newParseTokens = new ParseTokens(newTokens, newAnnotations);
							if (wordCounter >= 2) {
								throw new ParsingException("Name appears to have 2 or more omitted spaces!");
							}
							int currentNonFunctionalTermLength = StringTools.stringListToString(newTokens, "").length();
							if (currentNonFunctionalTermLength <= shortestNonFunctionalTermEncountered && !omittedWordParseTokensList.get(wordCounter).contains(newParseTokens)) {
								if (currentNonFunctionalTermLength < shortestNonFunctionalTermEncountered) {
									omittedWordParseTokensList.get(wordCounter).clear();
									shortestNonFunctionalTermEncountered = currentNonFunctionalTermLength;
								}
								omittedWordParseTokensList.get(wordCounter).add(newParseTokens);
							}
							wordCounter++;
							newAnnotations = new ArrayList<Character>();
							newTokens = new ArrayList<String>();
						}
					}
					for (Character annotation : annotationList) {
						newAnnotations.add(annotation);
						newTokens.add(tokens.get(annotPos));
						annotPos++;
					}
					if (functionalTermNext) {
						ParseTokens newParseTokens = new ParseTokens(newTokens, newAnnotations);
						if (wordCounter >= 2) {
							throw new ParsingException("Name appears to have 2 or more omitted spaces!");
						}
						int currentFunctionalTermLength = StringTools.stringListToString(newTokens, "").length();
						if (currentFunctionalTermLength >= longestFunctionalTermEncountered && !omittedWordParseTokensList.get(wordCounter).contains(newParseTokens)) {
							if (currentFunctionalTermLength > longestFunctionalTermEncountered) {
								omittedWordParseTokensList.get(wordCounter).clear();
								longestFunctionalTermEncountered = currentFunctionalTermLength;
							}
							omittedWordParseTokensList.get(wordCounter).add(newParseTokens);
						}
						wordCounter++;
						newAnnotations = new ArrayList<Character>();
						newTokens = new ArrayList<String>();
					}
				}
				if (!newAnnotations.isEmpty()) {
					ParseTokens newParseTokens = new ParseTokens(newTokens, newAnnotations);
					if (wordCounter >= 2) {
						throw new ParsingException("Name appears to have 2 or more omitted spaces!");
					}
					int currentNonFunctionalTermLength = StringTools.stringListToString(newTokens, "").length();
					if (currentNonFunctionalTermLength <= shortestNonFunctionalTermEncountered && !omittedWordParseTokensList.get(wordCounter).contains(newParseTokens)) {
						if (currentNonFunctionalTermLength < shortestNonFunctionalTermEncountered) {
							omittedWordParseTokensList.get(wordCounter).clear();
							shortestNonFunctionalTermEncountered = currentNonFunctionalTermLength;
						}
						omittedWordParseTokensList.get(wordCounter).add(newParseTokens);
					}
					wordCounter++;
				}
			} else {
				wellFormedParseTokens.add(parseTokens);
			}
		}
		List<ParseWord> parseWords = new ArrayList<ParseWord>();
		if (!wellFormedParseTokens.isEmpty()) {
			parseWords.add(new ParseWord(chemicalName, wellFormedParseTokens));
		} else {
			for (List<ParseTokens> omittedWordParseTokens : omittedWordParseTokensList) {
				parseWords.add(new ParseWord(StringTools.stringListToString(omittedWordParseTokens.get(0).getTokens(), ""), omittedWordParseTokens));
			}
		}
		return parseWords;
	}

	/**Groups the token annotations for a given word into substituent/s and/or a maingroup and/or functionalTerm by
	 * looking for the endOfSubstituent/endOfMainGroup/endOfFunctionalTerm annotations
	 *
	 * @param annots The annotations for a word.
	 * @return A List of lists of annotations, each list corresponds to a substituent/maingroup/functionalTerm
	 */
	public static List<List<Character>> chunkAnnotations(List<Character> annots) {
		LinkedList<List<Character>> chunkList = new LinkedList<List<Character>>();
		List<Character> currentTerm = new ArrayList<Character>();
		for (Character annot : annots) {
			currentTerm.add(annot);
			if (annot.equals(endOfSubstituent) || annot.equals(endOfMainGroup) || annot.equals(endOfFunctionalTerm)) {
				chunkList.add(currentTerm);
				currentTerm = new ArrayList<Character>();
			}
		}
		return chunkList;
	}
}
