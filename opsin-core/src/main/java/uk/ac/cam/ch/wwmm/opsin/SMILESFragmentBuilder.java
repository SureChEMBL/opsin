package uk.ac.cam.ch.wwmm.opsin;

import java.util.*;

import static uk.ac.cam.ch.wwmm.opsin.OpsinTools.*;
import static uk.ac.cam.ch.wwmm.opsin.XmlDeclarations.*;
import uk.ac.cam.ch.wwmm.opsin.Bond.SMILES_BOND_DIRECTION;
import uk.ac.cam.ch.wwmm.opsin.BondStereo.BondStereoValue;

/** A builder for fragments specified as SMILES. A slightly custom SMILES dialect is used.
 * It includes all common features of SMILES and a few useful extensions:
 * | is used within a square bracketed element to directly set valency e.g. [P|5]. This is the same as using the lambda convention
 * sb/te are allowed (aromatic antimony/tellurium):
 * H? e.g. [SeH?] is used to indicate that the atom should use the default valency. It is equivalent to not using square brackets for organic atoms
 *
 * Allowed:
 * Organic elements B,C,N,O,P,S,F,Cl,Br,I (square brackets not required)
 * Aromatic elements c,n,o,p,s (square brackets not required) si,as,se,sb,te (square brackets required) Note that the inclusion of si/sb/te are an unofficial extension
 * =, # for bond orders
 * . for disconnection
 * (, ) for branching
 * [, ] for placing inorganic elements within and specifying charge. Allowed: [Al3+] or [Al+++]
 * 012345679 - ring closures
 * %10 %99 - more ring closures (%100 is ring closure %10 and 0 as in normal SMILES)
 * / and \ to set double bond stereochemistry to cis/trans
 * @ and @@ to set tetrahedral stereochemistry as in SMILES.
 * Hx where x is a digit is used to sort of set the hydrogen. In actuality the valency of the atom is derived and a valency hint added to the atom
 * This valency hint is the minimum valency that atom may be in. H? as an extension gives you the lowest acceptable valency.
 * |3 |5 etc. can be used to set the valency of an atom e.g.  [Se|2]
 *
 * Also, an = or # at the start of the string indicates that the group attaches to its parent group via a double or triple bond.
 *
 * A -,=,# on the end indicates that in the absence of locants, other groups attach to
 * *it* via the atom at the end of the string, not at the start of the string with -,=,# meaning single,double or triple bond
 * This behaviour is overridden for certain suffixes to give different meanings to the atom the -,=,# is referring to
 *
 * @author ptc24
 * @author dl387
 *
 */
class SMILESFragmentBuilder {

	/**A "struct" to hold information on the parsing stack
	 *
	 * @author ptc24
	 *
	 */
	private static class StackFrame {
		/**The Atom currently under consideration.*/
		Atom atom;
		/**The order of the bond about to be formed.*/
		int bondOrder;
		/**Whether the bond is a \ or / bond for use in determining cis/trans.*/
		SMILES_BOND_DIRECTION slash;

		/**Creates a stack frame with given parameters.
		 *
		 * @param a An atom or null
		 * @param bondOrderVal The value for bondOrder.
		 */
		StackFrame(Atom a, int bondOrderVal) {
			atom = a;
			bondOrder = bondOrderVal;
			slash = null;
		}

		/**Creates a copy of an existing StackFrame.
		 *
		 * @param sf The stackframe to copy.
		 */
		StackFrame(StackFrame sf) {
			atom = sf.atom;
			bondOrder = sf.bondOrder;
		}
	}

	/**Organic Atoms.*/
	private static final Set<String> organicAtoms = new HashSet<String>();
	/**Aromatic Atoms.*/
	private static final Set<String> aromaticAtoms = new HashSet<String>();

	static {
		organicAtoms.add("B");
		organicAtoms.add("C");
		organicAtoms.add("N");
		organicAtoms.add("O");
		organicAtoms.add("P");
		organicAtoms.add("S");
		organicAtoms.add("F");
		organicAtoms.add("Cl");
		organicAtoms.add("Br");
		organicAtoms.add("I");

		aromaticAtoms.add("c");
		aromaticAtoms.add("n");
		aromaticAtoms.add("o");
		aromaticAtoms.add("p");
		aromaticAtoms.add("s");
		aromaticAtoms.add("si");
		aromaticAtoms.add("as");
		aromaticAtoms.add("se");
		aromaticAtoms.add("sb");
		aromaticAtoms.add("te");
	}

	/**Build a Fragment based on a SMILES string, with a null type/subType.
	 *
	 * @param smiles The SMILES string to build from.
	 * @param fragManager
     * @return The built fragment.
	 * @throws StructureBuildingException
	 */
	Fragment build(String smiles, FragmentManager fragManager) throws StructureBuildingException {
		return build(smiles, "", "", "", fragManager);
	}

	/**
	 * Build a Fragment based on a SMILES string.
	 * @param smiles The SMILES string to build from.
	 * @param type The type of fragment being built.
	 * @param subType The subtype of fragment being built.
	 * @param labelMapping A string indicating which locants to assign to each atom. Can be a slash delimited list, "numeric", "fusedRing" or "none". A value of "" is treated as synonymous to numeric
	 * @param fragManager
	 * @return Fragment The built fragment.
	 * @throws StructureBuildingException
	 */
	Fragment build(String smiles, String type, String subType, String labelMapping, FragmentManager fragManager) throws StructureBuildingException {
		if (smiles==null){
			throw new StructureBuildingException("SMILES specified is null");
		}
		if (type==null){
			throw new StructureBuildingException("type specified is null, use \"\" if a type is not desired ");
		}
		if (subType==null){
			throw new StructureBuildingException("subType specified is null, use \"\" if a subType is not desired ");
		}
		if (labelMapping==null){
			throw new StructureBuildingException("labelMapping is null use \"none\" if you do not want any numbering or \"numeric\" if you would like default numbering");
		}
		String[] labelMap = null;
		if (labelMapping.equals("")){
			labelMapping = NUMERIC_LABELS_VAL;
		}
		if(!labelMapping.equals(NONE_LABELS_VAL) && !labelMapping.equals(FUSEDRING_LABELS_VAL) ) {
			labelMap = MATCH_SLASH.split(labelMapping, -1);//place slash delimited labels into an array
		}
		int currentNumber = 1;
		Fragment currentFrag = new Fragment(type, subType);
		Stack<StackFrame> stack = new Stack<StackFrame>();
		stack.push(new StackFrame(null, 1));
		HashMap<String, StackFrame> closures = new HashMap<String, StackFrame>();//used for ring closures
		String tmpString = smiles;
		char firstCharacter =tmpString.charAt(0);
		if(firstCharacter == '-' || firstCharacter == '=' || firstCharacter == '#') {//used by OPSIN to specify the valency with which this fragment connects
			tmpString = tmpString.substring(1);
		}
		char lastCharacter =tmpString.charAt(tmpString.length()-1);
		if(lastCharacter == '-' || lastCharacter == '=' || lastCharacter == '#') {//used by OPSIN to specify the valency with which this fragment connects and to indicate it connects via the last atom in the SMILES
			tmpString = tmpString.substring(0, tmpString.length()-1);
		}

		while(tmpString.length() > 0) {
			Character nextChar = tmpString.charAt(0);
			tmpString = tmpString.substring(1);
			if(nextChar == '(') {
				stack.push(new StackFrame(stack.peek()));
			} else if(nextChar == ')') {
				stack.pop();
			} else if(nextChar == '-'){
				stack.peek().bondOrder = 1;
			} else if(nextChar == '='){
				if (stack.peek().bondOrder != 1){
					throw new StructureBuildingException("= in unexpected position: bond order already defined!");
				}
				stack.peek().bondOrder = 2;
			} else if(nextChar == '#'){
				if (stack.peek().bondOrder != 1){
					throw new StructureBuildingException("# in unexpected position: bond order already defined!");
				}
				stack.peek().bondOrder = 3;
			} else if(nextChar == '/'){
				if (stack.peek().slash!=null){
					throw new StructureBuildingException("/ in unexpected position: bond configuration already defined!");
				}
				stack.peek().slash = SMILES_BOND_DIRECTION.RSLASH;
			} else if(nextChar == '\\'){
				if (stack.peek().slash != null){
					throw new StructureBuildingException("\\ in unexpected position: bond configuration already defined!");
				}
				stack.peek().slash = SMILES_BOND_DIRECTION.LSLASH;
			} else if(nextChar == '.'){
				stack.peek().atom = null;
			} else if(Character.isLetter(nextChar)) {//organic atoms
		        String elementType = String.valueOf(nextChar);
		        boolean spareValency =false;
		        if(Character.isUpperCase(nextChar)) {//normal atoms
					if(tmpString.length() > 0 && Character.isLowerCase(tmpString.charAt(0)) && organicAtoms.contains(elementType + tmpString.substring(0,1))) {
						elementType += tmpString.substring(0,1);
						tmpString = tmpString.substring(1);
					}
					else if (!organicAtoms.contains(elementType)){
						throw new StructureBuildingException(elementType +" is not an organic Element. If it is actually an element it should be in square brackets");
					}
		        }
		        else if(Character.isLowerCase(nextChar)) {//aromatic atoms
					if (!aromaticAtoms.contains(elementType)){
						throw new StructureBuildingException(elementType +" is not an aromatic Element. If it is actually an element it should not be in lower case");
					}
					elementType = elementType.toUpperCase();
					spareValency =true;
		        }
				Atom atom = fragManager.createAtom(elementType, currentFrag);
				atom.setSpareValency(spareValency);
				if(labelMapping.equals(NUMERIC_LABELS_VAL)) {
					atom.addLocant(Integer.toString(currentNumber));
				} else if (labelMap !=null){
					String labels[] = MATCH_COMMA.split(labelMap[currentNumber-1]);
                    for (String label : labels) {
                        if (!label.equals("")) {
                            atom.addLocant(label);
                        }
                    }
				}
				currentFrag.addAtom(atom);
				if(stack.peek().atom !=null) {
					Bond b = fragManager.createBond(stack.peek().atom, atom, stack.peek().bondOrder);
					if (stack.peek().slash!=null){
						b.setSmilesStereochemistry(stack.peek().slash);
						stack.peek().slash = null;
					}
					if (stack.peek().atom.getAtomParity()!=null){
						addAtomToAtomParity(stack.peek().atom.getAtomParity(), atom);
					}
				}
				stack.peek().atom = atom;
				stack.peek().bondOrder = 1;
				currentNumber++;
			} else if(nextChar == '[') {//square brackets- contain non-organic atoms and are used to unambiguously set charge/chirality etc.
				int indexOfRightSquareBracket = tmpString.indexOf(']');
                if (indexOfRightSquareBracket == -1) {
                    throw new StructureBuildingException("[ without matching \"]\"");
                }
                String atomString = tmpString.substring(0, indexOfRightSquareBracket);//the contents of the square bracket
                tmpString = tmpString.substring(indexOfRightSquareBracket +1);
             // isotope
                String isotope = "";
				while(atomString.length() > 0 && Character.isDigit(atomString.charAt(0))) {
					isotope += atomString.charAt(0);
					atomString = atomString.substring(1);
				}

		        if (atomString.length() > 0){
		        	nextChar = atomString.charAt(0);
		        	atomString = atomString.substring(1);
		        }
		        else{
		        	throw new StructureBuildingException("No element found in square brackets");
		        }
		// elementType
		        String elementType = String.valueOf(nextChar);
		        boolean spareValency = false;
		        if(Character.isUpperCase(nextChar)) {//normal atoms
					if(atomString.length() > 0 && Character.isLowerCase(atomString.charAt(0))) {
						elementType += atomString.substring(0,1);
						atomString = atomString.substring(1);
					}
		        }
		        else if(Character.isLowerCase(nextChar)) {//aromatic atoms
					if(atomString.length() > 0 && Character.isLowerCase(atomString.charAt(0))) {
						if (aromaticAtoms.contains(elementType + atomString.substring(0,1))){
							elementType = elementType.toUpperCase() + atomString.substring(0,1);
							atomString = atomString.substring(1);
						}
						else{
							throw new StructureBuildingException(elementType + atomString.substring(0,1) +" is not an aromatic Element. If it is actually an element it should not be in lower case");
						}
					}
					else{
						if (!aromaticAtoms.contains(elementType)){
							throw new StructureBuildingException(elementType +" is not an aromatic Element.");
						}
						elementType = elementType.toUpperCase();
					}
					spareValency =true;
		        }
		        else if (elementType.equals("*")){
		        	elementType = "R";
		        }
		        else{
		        	throw new StructureBuildingException(elementType +" is not a valid element type!");
		        }
				Atom atom = fragManager.createAtom(elementType, currentFrag);
				atom.setSpareValency(spareValency);
		        if (!isotope.equals("")){
		        	atom.setIsotope(Integer.parseInt(isotope));
		        }
				if(labelMapping.equals(NUMERIC_LABELS_VAL)) {
					atom.addLocant(Integer.toString(currentNumber));
				} else if (labelMap !=null){
					String labels[] = MATCH_COMMA.split(labelMap[currentNumber-1]);
                    for (String label : labels) {
                        if (!label.equals("")) {
                            atom.addLocant(label);
                        }
                    }
				}
				currentFrag.addAtom(atom);
				if(stack.peek().atom != null) {
					Bond b = fragManager.createBond(stack.peek().atom, atom, stack.peek().bondOrder);
					if (stack.peek().slash!=null){
						b.setSmilesStereochemistry(stack.peek().slash);
						stack.peek().slash = null;
					}
					if (stack.peek().atom.getAtomParity()!=null){
						addAtomToAtomParity(stack.peek().atom.getAtomParity(), atom);
					}
				}
				Atom previousAtom = stack.peek().atom;//needed for setting atomParity elements up
				stack.peek().atom = atom;
				stack.peek().bondOrder = 1;
				currentNumber++;

		        Integer hydrogenCount =0;
		        int charge = 0;
		        Boolean chiralitySet = false;
		        while (atomString.length()>0){
		        	nextChar = atomString.charAt(0);
		        	atomString = atomString.substring(1);
		        	if(nextChar == '@') {// chirality-sets atom parity
		        		if (chiralitySet){
		        			throw new StructureBuildingException("Atom parity appeared to be specified twice for an atom in a square bracket!");
		        		}
		        		atomString = processTetrahedralStereochemistry(atomString, atom, previousAtom);
						chiralitySet = true;
		 			}
		            else if (nextChar == 'H'){// hydrogenCount
		            	if (hydrogenCount ==null || hydrogenCount != 0){
		            		throw new StructureBuildingException("Hydrogen count appeared to be specified twice for an atom in a square bracket!");
		            	}
	            		if (atomString.length() > 0 && atomString.charAt(0)=='?'){
	            			atomString = atomString.substring(1);
	            			hydrogenCount=null;
	            		}
	            		else{
			            	String hydrogenCountString ="";
	    					while(atomString.length() > 0 && Character.isDigit(atomString.charAt(0))) {
	    						hydrogenCountString += atomString.substring(0,1);
	    						atomString = atomString.substring(1);
	    					}
	    					if (hydrogenCountString.equals("")){
	    						hydrogenCount=1;
	    					}
	    					else{
	    						hydrogenCount = Integer.parseInt(hydrogenCountString);
	    					}
	    					if (atom.hasSpareValency()) {
	    						if ((!elementType.equals("C") && !elementType.equals("Si")) || hydrogenCount >=2){
		    						currentFrag.addIndicatedHydrogen(atom);
	    						}
	    					}
	            		}
		            }
		            else if(nextChar == '+' || nextChar == '-') {// formalCharge
		            	if (charge != 0){
		            		throw new StructureBuildingException("Charge appeared to be specified twice for an atom in a square bracket!");
		            	}
	    				charge = nextChar == '+' ? 1 : -1;
    					String changeChargeStr = "";
    					int changeCharge = 1;
    					while(atomString.length() > 0 && Character.isDigit(atomString.charAt(0))) {//e.g. [C+2]
    						changeChargeStr+= atomString.substring(0,1);
    						atomString = atomString.substring(1);
    					}
    					if (changeChargeStr.equals("")){
    						while(atomString.length() > 0){//e.g. [C++]
    							nextChar = atomString.charAt(0);
    							if (nextChar == '+'){
    								if (charge != 1){
    									throw new StructureBuildingException("Atom has both positive and negative charges specified!");//e.g. [C+-]
    								}
    							}
    							else if (nextChar == '-'){
    								if (charge != -1){
    									throw new StructureBuildingException("Atom has both negative and positive charges specified!");
    								}
    							}
    							else{
    								break;
    							}
    							changeCharge++;
        						atomString = atomString.substring(1);
        					}
    					}
    					changeCharge = changeChargeStr.equals("") ? changeCharge : Integer.parseInt(changeChargeStr);
    					atom.setCharge(atom.getCharge() + (charge * changeCharge) );
		            }
		            else if(nextChar == '|') {
						String lambda = "";
						while(atomString.length() > 0 && Character.isDigit(atomString.charAt(0))) {
							lambda += atomString.substring(0,1);
							atomString = atomString.substring(1);
						}
						atom.setLambdaConventionValency(Integer.parseInt(lambda));
					}
		            else{
		            	throw new StructureBuildingException("Unexpected character found in square bracket");
		            }
		        }
				atom.setProperty(Atom.SMILES_HYDROGEN_COUNT, hydrogenCount);
			} else if(Character.isDigit(nextChar)|| nextChar == '%') {
				tmpString = processRingOpeningOrClosure(fragManager, stack, closures, tmpString, nextChar);
			}
			else{
				throw new StructureBuildingException(nextChar + " is in an unexpected position. Check this is not a mistake and that this feature of SMILES is supported by OPSIN's SMILES parser");
			}
		}
		if (labelMap != null && labelMap.length >= currentNumber ){
			throw new StructureBuildingException("Group numbering has been invalidly defined in resource file: labels: " +labelMap.length + ", atoms: " + (currentNumber -1) );
		}
		if (!closures.isEmpty()){
			throw new StructureBuildingException("Unmatched ring opening");
		}

		if(labelMapping.equals(FUSEDRING_LABELS_VAL)) {//fragment is a fusedring with atoms in the correct order for fused ring numbering
			//this will do stuff like changing labels from 1,2,3,4,5,6,7,8,9,10->1,2,3,4,4a,5,6,7,8,8a
			FragmentTools.relabelFusedRingSystem(currentFrag);
		}
		List<Atom> atomList =currentFrag.getAtomList();
		verifyAndTakeIntoAccountLonePairsInAtomParities(atomList);
		addBondStereoElements(currentFrag);

		if(lastCharacter == '-' || lastCharacter == '=' || lastCharacter == '#') {
			Atom lastAtom = stack.peek().atom;//note that in something like C(=O)- this would be the carbon not the oxygen
			if (lastCharacter == '#'){
				currentFrag.addOutAtom(lastAtom, 3, true);
			}
			else if (lastCharacter == '='){
				currentFrag.addOutAtom(lastAtom, 2, true);
			}
			else{
				currentFrag.addOutAtom(lastAtom, 1, true);
			}
		}

		if(firstCharacter == '-'){
			currentFrag.addOutAtom(currentFrag.getFirstAtom(),1, true);
		}
		else if(firstCharacter == '='){
			currentFrag.addOutAtom(currentFrag.getFirstAtom(),2, true);
		}
		else if (firstCharacter == '#'){
			currentFrag.addOutAtom(currentFrag.getFirstAtom(),3, true);
		}

		for (Atom atom : atomList) {
			if (atom.getProperty(Atom.SMILES_HYDROGEN_COUNT)!=null && atom.getLambdaConventionValency() ==null){
				setupAtomValency(fragManager, atom);
			}
		}
		CycleDetector.assignWhetherAtomsAreInCycles(currentFrag);
		return currentFrag;
	}

	/**
	 * Adds an atomParity element to the given atom using the descriptor in atomString
	 * @param atomString
	 * @param atom
	 * @param previousAtom
	 * @return 
	 */
	private String processTetrahedralStereochemistry(String atomString, Atom atom, Atom previousAtom){
		Boolean chiralityClockwise = false;
		if (atomString.length() > 0 && atomString.charAt(0) == '@'){
			chiralityClockwise = true;
			atomString = atomString.substring(1);
		}
		AtomParity atomParity;
		if (chiralityClockwise){
			atomParity = new AtomParity(new Atom[4], 1);
		}
		else{
			atomParity = new AtomParity(new Atom[4], -1);
		}
		Atom[] atomRefs4 = atomParity.getAtomRefs4();
		int indice =0;
		if (previousAtom !=null){
			atomRefs4[indice] = previousAtom;
			indice++;
		}
		if (atomString.length() > 0 && atomString.charAt(0) == 'H'){
			atomRefs4[indice] = AtomParity.hydrogen;
		}
		atom.setAtomParity(atomParity);
		return atomString;
	}

	/**
	 * Process ring openings and closings e.g. the two 1s in c1ccccc1
	 * @param fragManager
	 * @param stack
	 * @param closures
	 * @param tmpString
	 * @param nextChar
	 * @return
	 * @throws StructureBuildingException
	 */
	private String processRingOpeningOrClosure(FragmentManager fragManager,
			Stack<StackFrame> stack, HashMap<String, StackFrame> closures,
			String tmpString, Character nextChar)
			throws StructureBuildingException {
		String closure = String.valueOf(nextChar);
		if(nextChar == '%') {
			if (tmpString.length() >=2 && Character.isDigit(tmpString.charAt(0)) && Character.isDigit(tmpString.charAt(1))) {
				closure = tmpString.substring(0,2);
				tmpString = tmpString.substring(2);
			}
			else{
				throw new StructureBuildingException("A ring opening indice after a % must be two digits long");
			}
		}
		if(closures.containsKey(closure)) {
			processRingClosure(fragManager, stack, closures, closure);
		} else {
			if (stack.peek().atom==null){
				throw new StructureBuildingException("A ring opening has appeared before any atom!");
			}
			processRingOpening(stack, closures, closure);
		}
		return tmpString;
	}

	private void processRingOpening(Stack<StackFrame> stack,
			HashMap<String, StackFrame> closures, String closure) throws StructureBuildingException {
		StackFrame sf = new StackFrame(stack.peek());
		if (stack.peek().slash!=null){
			sf.slash = stack.peek().slash;
			stack.peek().slash = null;
		}
		if (sf.atom.getAtomParity()!=null){//replace ringclosureX with actual reference to id when it is known
			Atom dummyRingClosureAtom = new Atom(closure);
			addAtomToAtomParity(sf.atom.getAtomParity(), dummyRingClosureAtom);
		}
		closures.put(closure, sf);
		stack.peek().bondOrder = 1;
	}

	private void addAtomToAtomParity(AtomParity atomParity, Atom atom) throws StructureBuildingException {
		Atom[] atomRefs4 = atomParity.getAtomRefs4();
		boolean setAtom =false;
		for (int i = 0; i < atomRefs4.length; i++) {
			if (atomRefs4[i] ==null){
				atomRefs4[i] = atom;
				setAtom =true;
				break;
			}
		}
		if (!setAtom){
			throw new StructureBuildingException("Tetrahedral stereocentre specified in SMILES appears to involve more than 4 atoms");
		}
	}

	private void processRingClosure(FragmentManager fragManager,
			Stack<StackFrame> stack, HashMap<String, StackFrame> closures,
			String closure) throws StructureBuildingException {
		StackFrame sf = closures.remove(closure);
		int bondOrder = 1;
		if(sf.bondOrder > 1) {
			if(stack.peek().bondOrder > 1 && sf.bondOrder != stack.peek().bondOrder){
				throw new StructureBuildingException("ring closure has two different bond orders specified!");
			}
			bondOrder = sf.bondOrder;
		} else if(stack.peek().bondOrder > 1) {
			bondOrder = stack.peek().bondOrder;
		}
		Bond b;
		if (stack.peek().slash ==null){
			b = fragManager.createBond(sf.atom, stack.peek().atom, bondOrder);
		}
		else{
			b = fragManager.createBond(stack.peek().atom, sf.atom, bondOrder);//special case e.g. CC1=C/F.O\1  Bond is done from the O to the the C due to the presence of the \
		}
		if(sf.slash !=null) {
			if(stack.peek().slash !=null) {
				if (sf.slash.equals(stack.peek().slash)){
					throw new StructureBuildingException("Contradictory double bond stereoconfiguration");
				}
			}
			else{
				b.setSmilesStereochemistry(sf.slash);
			}
		} else if(stack.peek().slash !=null) {
			b.setSmilesStereochemistry(stack.peek().slash);
			stack.peek().slash = null;
		}
		if (stack.peek().atom.getAtomParity()!=null){
			AtomParity atomParity = stack.peek().atom.getAtomParity();
			addAtomToAtomParity(atomParity, sf.atom);
		}
		if (sf.atom.getAtomParity()!=null){//replace dummy atom with actual atom e.g. N[C@@H]1C.F1 where the 1 initially holds a dummy atom before being replaced with the F atom
			AtomParity atomParity = sf.atom.getAtomParity();
			Atom[] atomRefs4 = atomParity.getAtomRefs4();
			boolean replacedAtom =false;
			for (int i = 0; i < atomRefs4.length; i++) {
				if (atomRefs4[i] !=null && atomRefs4[i].getElement().equals(closure)){
					atomRefs4[i] = stack.peek().atom;
					replacedAtom =true;
					break;
				}
			}
			if (!replacedAtom){
				throw new StructureBuildingException("Unable to find ring closure atom in atomRefs4 of atomparity when building SMILES");
			}
		}
		stack.peek().bondOrder = 1;
	}

	private void verifyAndTakeIntoAccountLonePairsInAtomParities(List<Atom> atomList) throws StructureBuildingException {
		for (Atom atom : atomList) {
			AtomParity atomParity =atom.getAtomParity();
			if (atomParity!=null){
				Atom[] atomRefs4 = atomParity.getAtomRefs4();
				int nullAtoms =0;
				int hydrogen =0;
				for (Atom atomRefs4Atom : atomRefs4) {
					if (atomRefs4Atom ==null){
						nullAtoms++;
					}
					else if (atomRefs4Atom.equals(AtomParity.hydrogen)){
						hydrogen++;
					}
				}
				if (nullAtoms!=0){
					if (nullAtoms ==1 && hydrogen==0 && (atom.getElement().equals("S") || atom.getElement().equals("Se"))){//special case where lone pair is part of the tetrahedron
						if (atomList.indexOf(atomRefs4[0]) < atomList.indexOf(atom)){//is there an atom in the SMILES in front of the stereocentre?
							atomRefs4[3] = atomRefs4[2];
							atomRefs4[2] = atomRefs4[1];
							atomRefs4[1] = atom;
						}
						else{
							atomRefs4[3] = atomRefs4[2];
							atomRefs4[2] = atomRefs4[1];
							atomRefs4[1] = atomRefs4[0];
							atomRefs4[0] = atom;
						}
					}
					else{
						throw new StructureBuildingException("SMILES is malformed. Tetrahedral stereochemistry defined on a non tetrahedral centre");
					}
				}
			}
		}
	}

	private void addBondStereoElements(Fragment currentFrag) throws StructureBuildingException {
		Set<Bond> bonds = currentFrag.getBondSet();
		for (Bond centralBond : bonds) {//identify cases of E/Z stereochemistry and add appropriate bondstereo tags
			if (centralBond.getOrder()==2){

				List<Bond> fromAtomBonds =centralBond.getFromAtom().getBonds();
				for (Bond preceedingBond : fromAtomBonds) {
					if (preceedingBond.getSmilesStereochemistry()!=null){
						List<Bond> toAtomBonds = centralBond.getToAtom().getBonds();
						for (Bond followingBond : toAtomBonds) {
							if (followingBond.getSmilesStereochemistry()!=null){//now found a double bond surrounded by two bonds with slashs
								boolean upFirst;
								boolean upSecond;
								Atom atom2 = centralBond.getFromAtom();
								Atom atom1;
								if (atom2 == preceedingBond.getToAtom()){
									atom1 = preceedingBond.getFromAtom();
								}
								else{
									atom1 = preceedingBond.getToAtom();
								}
								Atom atom3 = centralBond.getToAtom();
								Atom atom4;
								if (atom3 == followingBond.getFromAtom()){
									atom4 = followingBond.getToAtom();
								}
								else{
									atom4 = followingBond.getFromAtom();
								}
								if (preceedingBond.getSmilesStereochemistry() == SMILES_BOND_DIRECTION.LSLASH){
									upFirst = preceedingBond.getToAtom() == atom2;//in normally constructed SMILES this will be the case but you could write C(/F)=C/F instead of F\C=C/F
								}
								else if (preceedingBond.getSmilesStereochemistry() == SMILES_BOND_DIRECTION.RSLASH){
									upFirst = preceedingBond.getToAtom() != atom2;
								}
								else{
									throw new StructureBuildingException(preceedingBond.getSmilesStereochemistry() + " is not a slash!");
								}

								if (followingBond.getSmilesStereochemistry() == SMILES_BOND_DIRECTION.LSLASH){
									upSecond = followingBond.getFromAtom() != atom3;
								}
								else if (followingBond.getSmilesStereochemistry() == SMILES_BOND_DIRECTION.RSLASH){
									upSecond = followingBond.getFromAtom() == atom3;
								}
								else{
									throw new StructureBuildingException(followingBond.getSmilesStereochemistry() + " is not a slash!");
								}
								BondStereoValue cisTrans = upFirst == upSecond ? BondStereoValue.CIS : BondStereoValue.TRANS;
								if (centralBond.getBondStereo()!=null){
									//double bond has redundant specification e.g. C/C=C\\1/NC1 hence need to check it is consistent
									Atom[] atomRefs4 = centralBond.getBondStereo().getAtomRefs4();
									if (atomRefs4[0].equals(atom1) || atomRefs4[3].equals(atom4)){
										if (centralBond.getBondStereo().getBondStereoValue().equals(cisTrans)){
											throw new StructureBuildingException("Contradictory double bond stereoconfiguration");
										}
									}
									else{
										if (!centralBond.getBondStereo().getBondStereoValue().equals(cisTrans)){
											throw new StructureBuildingException("Contradictory double bond stereoconfiguration");
										}
									}
								}
								else{
									Atom[] atomRefs4= new Atom[4];
									atomRefs4[0] =atom1;
									atomRefs4[1] =atom2;
									atomRefs4[2] =atom3;
									atomRefs4[3] =atom4;
									centralBond.setBondStereoElement(atomRefs4, cisTrans);
								}
							}
						}
					}
				}
			}
		}
		for (Bond bond : bonds) {
			bond.setSmilesStereochemistry(null);
		}
	}
	
	/**
	 * Utilises the atom's hydrogen count as set by the SMILES as well as incoming valency to determine the atom's valency
	 * If the atom is charged whether protons have been added or removed will also need to be determined
	 * @param fragManager 
	 * @param atom
	 * @throws StructureBuildingException 
	 */
	private void setupAtomValency(FragmentManager fragManager, Atom atom) throws StructureBuildingException {
		int hydrogenCount = atom.getProperty(Atom.SMILES_HYDROGEN_COUNT);
		int incomingValency = atom.getIncomingValency() + hydrogenCount +atom.getOutValency();
		int charge = atom.getCharge();
		int absoluteCharge =Math.abs(charge);
		String element =atom.getElement();
		if (atom.hasSpareValency()){
			Integer hwValency;
			if (element.equals("C")){
				hwValency =4;
			}
			else{
				hwValency = ValencyChecker.getHWValency(element);
				if (hwValency == null){
					throw new StructureBuildingException(element +" is not expected to be aromatic!");
				}
			}
			if (incomingValency < (hwValency + absoluteCharge)){
				incomingValency++;
			}
		}
		Integer defaultVal = ValencyChecker.getDefaultValency(element);
		if (defaultVal !=null){//s or p block element
			if (defaultVal != incomingValency || charge !=0){		
				if (Math.abs(incomingValency - defaultVal)==Math.abs(charge)){
					atom.setProtonsExplicitlyAddedOrRemoved(incomingValency - defaultVal);
				}
				else{
					Integer[] unchargedStableValencies = ValencyChecker.getPossibleValencies(element, 0);
					boolean hasPlausibleValency =false;
					for (Integer unchargedStableValency : unchargedStableValencies) {
						if (Math.abs(incomingValency - unchargedStableValency)==Math.abs(charge)){
							atom.setProtonsExplicitlyAddedOrRemoved(incomingValency - unchargedStableValency);
							//we strictly set the valency if a charge is specified but are more loose about things if uncharged e.g. allow penta substituted phosphine
							if (charge != 0) {
								atom.setLambdaConventionValency(unchargedStableValency);
							}
							else{
								atom.setMinimumValency(incomingValency);
							}
							hasPlausibleValency=true;
							break;
						}
					}
					if (!hasPlausibleValency){//could be something like [Sn] which would be expected to be attached to later
						atom.setMinimumValency(incomingValency);
					}
				}
			}
		}
		else{
			if (hydrogenCount >0){//make hydrogen explicit
				Fragment frag =atom.getFrag();
				for (int i = 0; i < hydrogenCount; i++) {
					Atom hydrogen = fragManager.createAtom("H", frag);
					fragManager.createBond(atom, hydrogen, 1);
				}
			}
		}
	}

}
