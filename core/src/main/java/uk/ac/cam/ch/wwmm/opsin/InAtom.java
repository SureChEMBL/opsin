package uk.ac.cam.ch.wwmm.opsin;

/**
 * Struct for an InAtom. As expected holds the atom.
 * valency gives the order of the bond to be created when connected.
 * Used in cases where a bond must be created to an atom e.g. for the parent fragment in multiplicative nomenclature
 *
 * @author dl387
 *
 */
class InAtom {

	private Atom atom;
	private int valency;
	
	InAtom(Atom atom, int valency) {
		this.atom = atom;
		this.valency = valency;
		atom.addOutValency(valency);
	}
	Atom getAtom() {
		return atom;
	}

	void setAtom(Atom atom) {
		this.atom = atom;
	}

	int getValency() {
		return valency;
	}

	void setValency(int valency) {
		atom.addOutValency(valency -this.valency);
		this.valency = valency;
	}
}