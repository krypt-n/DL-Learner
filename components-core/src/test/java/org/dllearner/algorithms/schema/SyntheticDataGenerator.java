/**
 * 
 */
package org.dllearner.algorithms.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.util.IRIShortFormProvider;
import org.semanticweb.owlapi.util.SimpleIRIShortFormProvider;

import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

/**
 * @author Lorenz Buehmann 
 * @since Oct 25, 2014
 */
public class SyntheticDataGenerator {
	
	private static final String NS = "http://dl-learner.org/data/";
	
	PrefixManager pm = new DefaultPrefixManager(NS);
	IRIShortFormProvider sfp = new SimpleIRIShortFormProvider();
	OWLOntologyManager man = OWLManager.createOWLOntologyManager();
	OWLDataFactory df = man.getOWLDataFactory();
	
	char classCharacter = 'A';
	
	Random rnd = new Random(123);
	
	OWLOntology ontology;
	
	public void createData(int nrOfClasses){
		try {
			ontology = man.createOntology();
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		}
		
		for (int i = 0; i < nrOfClasses; i++) {
			createClass(String.valueOf(classCharacter++));
		}
	}
	
	/**
	 * Connect sourceClass CS with targetClass CT by property P, i.e. add
	 * statements P(cs_i, ct_j) with i,j <= n
	 */
	private void connect(OWLClass sourceClass, OWLClass targetClass, OWLObjectProperty property, int nrOfConnections){
		// create individuals for sourceClass
		List<OWLIndividual> sourceIndividuals = addIndividuals(sourceClass, nrOfConnections);
		
		// create individuals for targetClass
		List<OWLIndividual> targetIndividuals = addIndividuals(targetClass, nrOfConnections);
		
		// add connections
		for (int i = 0; i < nrOfConnections; i++) {
			man.addAxiom(ontology, 
					df.getOWLObjectPropertyAssertionAxiom(
							property, 
							sourceIndividuals.get(i), 
							targetIndividuals.get(i)));
		}
	}
	
	/**
	 * Add n fresh individuals to given class.
	 */
	private List<OWLIndividual> addIndividuals(OWLClass cls, int n){
		List<OWLIndividual> individuals = new ArrayList<OWLIndividual>();
		
		for (int i = 0; i < n; i++) {
			OWLIndividual ind = df.getOWLNamedIndividual(sfp.getShortForm(cls.getIRI()) + i, pm);
			man.addAxiom(ontology, df.getOWLClassAssertionAxiom(cls, ind));
		}
		
		return individuals;
	}
	
	private OWLClass createClass(String name){
		return df.getOWLClass(name, pm);
	}
	
	private OWLObjectProperty createObjectProperty(String name){
		return df.getOWLObjectProperty(name, pm);
	}
	
	private OWLDataProperty createDataProperty(String name){
		return df.getOWLDataProperty(name, pm);
	}
	
	private OWLIndividual createIndividual(String name){
		return df.getOWLNamedIndividual(name, pm);
	}
	
	public static void main(String[] args) throws Exception {
		char c = 'A';
		c++;
		System.out.println(++c);
	}

}
