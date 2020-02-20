/**
 * Copyright (C) 2007 - 2016, Jens Lehmann
 *
 * This file is part of DL-Learner.
 *
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dllearner.reasoning;

import com.google.common.base.StandardSystemProperty;
import org.dllearner.kb.OWLAPIOntology;
import org.dllearner.kb.OWLOntologyKnowledgeSource;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.AxiomAnnotations;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.reasoner.impl.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.CollectionFactory;
import org.semanticweb.owlapi.util.OWLObjectPropertyManager;
import org.semanticweb.owlapi.util.Version;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;
import java.util.stream.Stream;

import static org.semanticweb.owlapi.model.parameters.Imports.INCLUDED;

/**
 * Author: Matthew Horridge<br>
 * The University of Manchester<br>
 * Information Management Group<br>
 * Date: 04-Dec-2009
 * </p>
 * This is a simple structural reasoner that essentially answers with told information.  It is incomplete.
 */
public class StructuralReasonerExtended extends OWLReasonerBase {

    private final ClassHierarchyInfo classHierarchyInfo = new ClassHierarchyInfo();

    private final ObjectPropertyHierarchyInfo objectPropertyHierarchyInfo = new ObjectPropertyHierarchyInfo();

    private final DataPropertyHierarchyInfo dataPropertyHierarchyInfo = new DataPropertyHierarchyInfo();

    private static final Version version = new Version(1, 0, 0, 0);

    private boolean interrupted = false;

    protected final ReasonerProgressMonitor pm;

    private boolean prepared = false;

	private OWLDataFactory df;

    /**
     * @param rootOntology the ontology
     * @param configuration the reasoner configuration
     * @param bufferingMode the buffering mode
     */
    public StructuralReasonerExtended(OWLOntology rootOntology, OWLReasonerConfiguration configuration, BufferingMode bufferingMode) {
        super(rootOntology, configuration, bufferingMode);
        pm = configuration.getProgressMonitor();
        prepareReasoner();
        df = rootOntology.getOWLOntologyManager().getOWLDataFactory();
    }

    @Nonnull
    @Override
    public String getReasonerName() {
        return "Structural Reasoner";
    }

    @Nonnull
    @Override
    public FreshEntityPolicy getFreshEntityPolicy() {
        return FreshEntityPolicy.ALLOW;
    }

    @Nonnull
    @Override
    public IndividualNodeSetPolicy getIndividualNodeSetPolicy() {
        return IndividualNodeSetPolicy.BY_NAME;
    }

    @Nonnull
    @Override
    public Version getReasonerVersion() {
        return version;
    }

    @Override
    protected void handleChanges(@Nonnull Set<OWLAxiom> addAxioms, @Nonnull Set<OWLAxiom> removeAxioms) {
        handleChanges(addAxioms, removeAxioms, classHierarchyInfo);
        handleChanges(addAxioms, removeAxioms, objectPropertyHierarchyInfo);
        handleChanges(addAxioms, removeAxioms, dataPropertyHierarchyInfo);
    }

    private <T extends OWLObject> void handleChanges(Set<OWLAxiom> added, Set<OWLAxiom> removed, HierarchyInfo<T> hierarchyInfo) {
        Set<T> sig = hierarchyInfo.getEntitiesInSignature(added);
        sig.addAll(hierarchyInfo.getEntitiesInSignature(removed));
        hierarchyInfo.processChanges(sig, added, removed);

    }

    @Override
    public void interrupt() {
        interrupted = true;
    }

    private void ensurePrepared() {
        if (!prepared) {
            prepareReasoner();
        }
    }

    /**
     * @throws ReasonerInterruptedException on interruption
     * @throws TimeOutException on timeout
     */
    public void prepareReasoner() throws ReasonerInterruptedException, TimeOutException {
        classHierarchyInfo.computeHierarchy();
        objectPropertyHierarchyInfo.computeHierarchy();
        dataPropertyHierarchyInfo.computeHierarchy();
        prepared = true;
    }

    @Override

    public void precomputeInferences(@Nonnull InferenceType... inferenceTypes) throws ReasonerInterruptedException, TimeOutException, InconsistentOntologyException {
        prepareReasoner();
    }

    @Override

    public boolean isPrecomputed(@Nonnull InferenceType inferenceType) {
        return true;
    }

    @Nonnull
    @Override
    public Set<InferenceType> getPrecomputableInferenceTypes() {
        return CollectionFactory.createSet(InferenceType.CLASS_HIERARCHY, InferenceType.OBJECT_PROPERTY_HIERARCHY, InferenceType.DATA_PROPERTY_HIERARCHY);
    }

    protected void throwExceptionIfInterrupted() {
        if (interrupted) {
            interrupted = false;
            throw new ReasonerInterruptedException();
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isConsistent() throws ReasonerInterruptedException, TimeOutException {
        return true;
    }

    @Override
    public boolean isSatisfiable(@Nonnull OWLClassExpression classExpression) throws ReasonerInterruptedException, TimeOutException, ClassExpressionNotInProfileException, FreshEntitiesException, InconsistentOntologyException {
        return !classExpression.isAnonymous() && !getEquivalentClasses(classExpression.asOWLClass()).contains(getDataFactory().getOWLNothing());
    }

    @Nonnull
    @Override
    public Node<OWLClass> getUnsatisfiableClasses() throws ReasonerInterruptedException, TimeOutException {
        return OWLClassNode.getBottomNode();
    }

    @Override
    public boolean isEntailed(@Nonnull OWLAxiom axiom) throws ReasonerInterruptedException, UnsupportedEntailmentTypeException, TimeOutException, AxiomNotInProfileException, FreshEntitiesException, InconsistentOntologyException {
    	boolean containsAxiom = getRootOntology().containsAxiom(axiom, INCLUDED, AxiomAnnotations.IGNORE_AXIOM_ANNOTATIONS);
		
		if(containsAxiom){
			return true;
		}
    	
    	if (axiom instanceof OWLClassAssertionAxiom) {// extension of OWL API code
			ensurePrepared();

			OWLClassExpression ce = ((OWLClassAssertionAxiom) axiom).getClassExpression();
			OWLIndividual individual = ((OWLClassAssertionAxiom) axiom).getIndividual();

			if (ce.isOWLThing()) {
				return true;
			} else if (ce.isOWLNothing()) {
				return false;
			} else if (!ce.isAnonymous()) {
                return getSubClasses(ce.asOWLClass(), false).getFlattened().stream()
                        .anyMatch(sub -> getRootOntology().containsAxiom(df.getOWLClassAssertionAxiom(sub, individual), INCLUDED, AxiomAnnotations.IGNORE_AXIOM_ANNOTATIONS));
            } else {
				if (ce instanceof OWLObjectIntersectionOf) {
                    return ((OWLObjectIntersectionOf) ce).getOperands().stream()
                            .allMatch(op -> isEntailed(df.getOWLClassAssertionAxiom(op, individual)));
				} else if (ce instanceof OWLObjectUnionOf) {
                    return ((OWLObjectUnionOf) ce).getOperands().stream()
                            .anyMatch(op -> isEntailed(df.getOWLClassAssertionAxiom(op, individual)));
				} else if (ce instanceof OWLObjectSomeValuesFrom) {
					OWLObjectPropertyExpression ope = ((OWLObjectSomeValuesFrom) ce).getProperty();
					
					OWLClassExpression filler = ((OWLObjectSomeValuesFrom) ce).getFiller();
					
					Set<OWLIndividualAxiom> axioms = getRootOntology().getAxioms(individual, INCLUDED);
					
					for (OWLIndividualAxiom curAx : axioms) {
						if(curAx instanceof OWLObjectPropertyAssertionAxiom && 
								ope.equals(((OWLObjectPropertyAssertionAxiom) curAx).getProperty())) {
							
							OWLIndividual object = ((OWLObjectPropertyAssertionAxiom) curAx).getObject();
							return isEntailed(df.getOWLClassAssertionAxiom(filler, object));
						}
					}
					return false;
				}
			}
		}
    	
    	return false;
    }

    @Override
    public boolean isEntailed(@Nonnull Set<? extends OWLAxiom> axioms) throws ReasonerInterruptedException, UnsupportedEntailmentTypeException, TimeOutException, AxiomNotInProfileException, FreshEntitiesException, InconsistentOntologyException {
        for (OWLAxiom ax : axioms) {
            assert ax != null;
            if (!getRootOntology().containsAxiom(ax, INCLUDED, AxiomAnnotations.IGNORE_AXIOM_ANNOTATIONS)) {
                return false;
            }
        }
        return true;
    }

    @Override

    public boolean isEntailmentCheckingSupported(@Nonnull AxiomType<?> axiomType) {
        return false;
    }

    @Nonnull
    @Override
    public Node<OWLClass> getTopClassNode() {
        ensurePrepared();
        return classHierarchyInfo.getEquivalents(getDataFactory().getOWLThing());
    }

    @Nonnull
    @Override
    public Node<OWLClass> getBottomClassNode() {
        ensurePrepared();
        return classHierarchyInfo.getEquivalents(getDataFactory().getOWLNothing());
    }

    @Nonnull
    @Override
    public NodeSet<OWLClass> getSubClasses(@Nonnull OWLClassExpression ce, boolean direct) throws InconsistentOntologyException, ClassExpressionNotInProfileException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        OWLClassNodeSet ns = new OWLClassNodeSet();
        if (!ce.isAnonymous()) {
            ensurePrepared();
            return classHierarchyInfo.getNodeHierarchyChildren(ce.asOWLClass(), direct, ns);
        }
        return ns;
    }

    @Nonnull
    @Override
    public NodeSet<OWLClass> getSuperClasses(@Nonnull OWLClassExpression ce, boolean direct) throws InconsistentOntologyException, ClassExpressionNotInProfileException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        OWLClassNodeSet ns = new OWLClassNodeSet();
        if (!ce.isAnonymous()) {
            ensurePrepared();
            return classHierarchyInfo.getNodeHierarchyParents(ce.asOWLClass(), direct, ns);
        }
        return ns;
    }

    @Nonnull
    @Override
    public Node<OWLClass> getEquivalentClasses(@Nonnull OWLClassExpression ce) throws InconsistentOntologyException, ClassExpressionNotInProfileException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        if (!ce.isAnonymous()) {
            return classHierarchyInfo.getEquivalents(ce.asOWLClass());
        }
        else {
            return new OWLClassNode();
        }
    }

    @Nonnull
    @Override
    public NodeSet<OWLClass> getDisjointClasses(@Nonnull OWLClassExpression ce) {
        ensurePrepared();
        OWLClassNodeSet nodeSet = new OWLClassNodeSet();
        if (!ce.isAnonymous()) {
            for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
                for (OWLDisjointClassesAxiom ax : ontology.getDisjointClassesAxioms(ce.asOWLClass())) {
                    for (OWLClassExpression op : ax.getClassExpressions()) {
                        if (!op.isAnonymous()) {
                            nodeSet.addNode(getEquivalentClasses(op));
                        }
                    }
                }
            }
        }
        return nodeSet;
    }

    @Nonnull
    @Override
    public Node<OWLObjectPropertyExpression> getTopObjectPropertyNode() {
        ensurePrepared();
        return objectPropertyHierarchyInfo.getEquivalents(getDataFactory().getOWLTopObjectProperty());
    }

    @Nonnull
    @Override
    public Node<OWLObjectPropertyExpression> getBottomObjectPropertyNode() {
        ensurePrepared();
        return objectPropertyHierarchyInfo.getEquivalents(getDataFactory().getOWLBottomObjectProperty());
    }

    @Nonnull
    @Override
    public NodeSet<OWLObjectPropertyExpression> getSubObjectProperties(@Nonnull OWLObjectPropertyExpression pe, boolean direct) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        OWLObjectPropertyNodeSet ns = new OWLObjectPropertyNodeSet();
        ensurePrepared();
        return objectPropertyHierarchyInfo.getNodeHierarchyChildren(pe, direct, ns);
    }

    @Nonnull
    @Override
    public NodeSet<OWLObjectPropertyExpression> getSuperObjectProperties(@Nonnull OWLObjectPropertyExpression pe, boolean direct) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        OWLObjectPropertyNodeSet ns = new OWLObjectPropertyNodeSet();
        ensurePrepared();
        return objectPropertyHierarchyInfo.getNodeHierarchyParents(pe, direct, ns);
    }

    @Nonnull
    @Override
    public Node<OWLObjectPropertyExpression> getEquivalentObjectProperties(@Nonnull OWLObjectPropertyExpression pe) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        return objectPropertyHierarchyInfo.getEquivalents(pe);
    }

    @Nonnull
    @Override

    public NodeSet<OWLObjectPropertyExpression> getDisjointObjectProperties(@Nonnull OWLObjectPropertyExpression pe) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return new OWLObjectPropertyNodeSet();
    }

    @Nonnull
    @Override
    public Node<OWLObjectPropertyExpression> getInverseObjectProperties(@Nonnull OWLObjectPropertyExpression pe) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        OWLObjectPropertyExpression inv = pe.getInverseProperty();
        return getEquivalentObjectProperties(inv);
    }

    @Nonnull
    @Override
    public NodeSet<OWLClass> getObjectPropertyDomains(@Nonnull OWLObjectPropertyExpression pe, boolean direct) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {

        ensurePrepared();
        DefaultNodeSet<OWLClass> result = new OWLClassNodeSet();
        for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
            for (OWLObjectPropertyDomainAxiom axiom : ontology.getObjectPropertyDomainAxioms(pe)) {
                result.addNode(getEquivalentClasses(axiom.getDomain()));
                if (!direct) {
                    result.addAllNodes(getSuperClasses(axiom.getDomain(), false).getNodes());
                }
            }

            for (OWLObjectPropertyExpression invPe : getInverseObjectProperties(pe).getEntities()) {
                for (OWLObjectPropertyRangeAxiom axiom : ontology.getObjectPropertyRangeAxioms(invPe)) {
                    result.addNode(getEquivalentClasses(axiom.getRange()));
                    if (!direct) {
                        result.addAllNodes(getSuperClasses(axiom.getRange(), false).getNodes());
                    }
                }
            }
        }
        return result;
    }

    @Nonnull
    @Override
    public NodeSet<OWLClass> getObjectPropertyRanges(@Nonnull OWLObjectPropertyExpression pe, boolean direct) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        DefaultNodeSet<OWLClass> result = new OWLClassNodeSet();
        for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
            for (OWLObjectPropertyRangeAxiom axiom : ontology.getObjectPropertyRangeAxioms(pe)) {
                result.addNode(getEquivalentClasses(axiom.getRange()));
                if (!direct) {
                    result.addAllNodes(getSuperClasses(axiom.getRange(), false).getNodes());
                }
            }
            for (OWLObjectPropertyExpression invPe : getInverseObjectProperties(pe).getEntities()) {
                for (OWLObjectPropertyDomainAxiom axiom : ontology.getObjectPropertyDomainAxioms(invPe)) {
                    result.addNode(getEquivalentClasses(axiom.getDomain()));
                    if (!direct) {
                        result.addAllNodes(getSuperClasses(axiom.getDomain(), false).getNodes());
                    }
                }
            }
        }
        return result;
    }

    @Nonnull
    @Override
    public Node<OWLDataProperty> getTopDataPropertyNode() {
        ensurePrepared();
        return dataPropertyHierarchyInfo.getEquivalents(getDataFactory().getOWLTopDataProperty());
    }

    @Nonnull
    @Override
    public Node<OWLDataProperty> getBottomDataPropertyNode() {
        ensurePrepared();
        return dataPropertyHierarchyInfo.getEquivalents(getDataFactory().getOWLBottomDataProperty());
    }

    @Nonnull
    @Override
    public NodeSet<OWLDataProperty> getSubDataProperties(@Nonnull OWLDataProperty pe, boolean direct) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        OWLDataPropertyNodeSet ns = new OWLDataPropertyNodeSet();
        return dataPropertyHierarchyInfo.getNodeHierarchyChildren(pe, direct, ns);
    }

    @Nonnull
    @Override
    public NodeSet<OWLDataProperty> getSuperDataProperties(@Nonnull OWLDataProperty pe, boolean direct) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        OWLDataPropertyNodeSet ns = new OWLDataPropertyNodeSet();
        return dataPropertyHierarchyInfo.getNodeHierarchyParents(pe, direct, ns);
    }

    @Nonnull
    @Override
    public Node<OWLDataProperty> getEquivalentDataProperties(@Nonnull OWLDataProperty pe) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        return dataPropertyHierarchyInfo.getEquivalents(pe);
    }

    @Nonnull
    @Override
    public NodeSet<OWLDataProperty> getDisjointDataProperties(@Nonnull OWLDataPropertyExpression pe) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        DefaultNodeSet<OWLDataProperty> result = new OWLDataPropertyNodeSet();
        for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
            for (OWLDisjointDataPropertiesAxiom axiom : ontology.getDisjointDataPropertiesAxioms(pe.asOWLDataProperty())) {
                for (OWLDataPropertyExpression dpe : axiom.getPropertiesMinus(pe)) {
                    if (!dpe.isAnonymous()) {
                        result.addNode(dataPropertyHierarchyInfo.getEquivalents(dpe.asOWLDataProperty()));
                        result.addAllNodes(getSubDataProperties(dpe.asOWLDataProperty(), false).getNodes());
                    }
                }
            }
        }
        return result;
    }

    @Nonnull
    @Override
    public NodeSet<OWLClass> getDataPropertyDomains(@Nonnull OWLDataProperty pe, boolean direct) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        DefaultNodeSet<OWLClass> result = new OWLClassNodeSet();
        for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
            for (OWLDataPropertyDomainAxiom axiom : ontology.getDataPropertyDomainAxioms(pe)) {
                result.addNode(getEquivalentClasses(axiom.getDomain()));
                if (!direct) {
                    result.addAllNodes(getSuperClasses(axiom.getDomain(), false).getNodes());
                }
            }
        }
        return result;
    }

    @Nonnull
    @Override
    public NodeSet<OWLClass> getTypes(@Nonnull OWLNamedIndividual ind, boolean direct) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        DefaultNodeSet<OWLClass> result = new OWLClassNodeSet();
        for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
            for (OWLClassAssertionAxiom axiom : ontology.getClassAssertionAxioms(ind)) {
                OWLClassExpression ce = axiom.getClassExpression();
                if (!ce.isAnonymous()) {
                    result.addNode(classHierarchyInfo.getEquivalents(ce.asOWLClass()));
                    if (!direct) {
                        result.addAllNodes(getSuperClasses(ce, false).getNodes());
                    }
                }
            }
        }
        return result;
    }

    @Nonnull
    @Override
    public NodeSet<OWLNamedIndividual> getInstances(@Nonnull OWLClassExpression ce, boolean direct) throws InconsistentOntologyException, ClassExpressionNotInProfileException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        DefaultNodeSet<OWLNamedIndividual> result = new OWLNamedIndividualNodeSet();
        if (!ce.isAnonymous()) {
            OWLClass cls = ce.asOWLClass();
            Set<OWLClass> clses = new HashSet<>();
            clses.add(cls);
            if (!direct) {
                clses.addAll(getSubClasses(cls, false).getFlattened());
            }
            for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
                for (OWLClass curCls : clses) {
                    for (OWLClassAssertionAxiom axiom : ontology.getClassAssertionAxioms(curCls)) {
                        OWLIndividual individual = axiom.getIndividual();
                        if (!individual.isAnonymous()) {
                            if (getIndividualNodeSetPolicy().equals(IndividualNodeSetPolicy.BY_SAME_AS)) {
                                result.addNode(getSameIndividuals(individual.asOWLNamedIndividual()));
                            }
                            else {
                                result.addNode(new OWLNamedIndividualNode(individual.asOWLNamedIndividual()));
                            }
                        }
                    }
                }
            }
        } else { // extension of OWL API implementation
        	if(ce instanceof OWLObjectIntersectionOf){
        		List<OWLClassExpression> operands = ((OWLObjectIntersectionOf) ce).getOperandsAsList();
        		
        		Set<Node<OWLNamedIndividual>> tmp = new HashSet<>();
        		
        		tmp.addAll(getInstances(operands.get(0), direct).getNodes());
        		
        		for (int i = 1; i < operands.size(); i++) {
        			tmp.retainAll(getInstances(operands.get(i), direct).getNodes());
				}
                result.addAllNodes(tmp);
        	} else if(ce instanceof OWLObjectUnionOf){
        		Set<OWLClassExpression> operands = ((OWLObjectUnionOf) ce).getOperands();
        		
        		for (OWLClassExpression op : operands) {
					result.addAllNodes(getInstances(op, direct).getNodes());
				}
        	} else if(ce instanceof OWLObjectSomeValuesFrom){
        		System.out.println(ce);
        		OWLObjectPropertyExpression ope = ((OWLObjectSomeValuesFrom) ce).getProperty();
        		OWLClassExpression filler = ((OWLObjectSomeValuesFrom) ce).getFiller();
                Set<OWLObjectPropertyExpression> properties = new HashSet<>();
                properties.add(ope.asOWLObjectProperty());
                if (!direct) {
                    properties.addAll(getSubObjectProperties(ope, false).getFlattened());
                }
        		for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
                    for (OWLObjectPropertyExpression curProp : properties) {
                        Set<OWLAxiom> referencingAxioms = ontology.getReferencingAxioms(curProp.asOWLObjectProperty());

							for (OWLAxiom axiom : referencingAxioms) {
                        	if(axiom instanceof OWLObjectPropertyAssertionAxiom){
                        		// check if object is instance of filler 
                        		OWLIndividual object = ((OWLObjectPropertyAssertionAxiom) axiom).getObject();
                        		if(!object.isAnonymous()){
                        			if(filler.isOWLThing() || getInstances(filler, false).containsEntity(object.asOWLNamedIndividual())){
                        				OWLIndividual subject = ((OWLObjectPropertyAssertionAxiom) axiom).getSubject();
                                        if (!subject.isAnonymous()) {
                                            if (getIndividualNodeSetPolicy().equals(IndividualNodeSetPolicy.BY_SAME_AS)) {
                                                result.addNode(getSameIndividuals(subject.asOWLNamedIndividual()));
                                            }
                                            else {
                                                result.addNode(new OWLNamedIndividualNode(subject.asOWLNamedIndividual()));
                                            }
                                        }
                        			}
                        		}
                        	}
                        }
                    }
                }
        	} else if(ce instanceof OWLObjectAllValuesFrom){
        		
        	}
        }
        return result;
    }

    @Nonnull
    @Override
    public NodeSet<OWLNamedIndividual> getObjectPropertyValues(@Nonnull OWLNamedIndividual ind, @Nonnull OWLObjectPropertyExpression pe) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        OWLNamedIndividualNodeSet result = new OWLNamedIndividualNodeSet();
        Node<OWLObjectPropertyExpression> inverses = getInverseObjectProperties(pe);
        for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
            for (OWLObjectPropertyAssertionAxiom axiom : ontology.getObjectPropertyAssertionAxioms(ind)) {
                if (!axiom.getObject().isAnonymous()) {
                    if (axiom.getProperty().equals(pe)) {
                        if (getIndividualNodeSetPolicy().equals(IndividualNodeSetPolicy.BY_SAME_AS)) {
                            result.addNode(getSameIndividuals(axiom.getObject().asOWLNamedIndividual()));
                        }
                        else {
                            result.addNode(new OWLNamedIndividualNode(axiom.getObject().asOWLNamedIndividual()));
                        }
                    }
                }
                // Inverse of pe
                if (axiom.getObject().equals(ind) && !axiom.getSubject().isAnonymous()) {
                    OWLObjectPropertyExpression invPe = axiom.getProperty().getInverseProperty();
                    if (!invPe.isAnonymous() && inverses.contains(invPe.asOWLObjectProperty())) {
                        if (getIndividualNodeSetPolicy().equals(IndividualNodeSetPolicy.BY_SAME_AS)) {
                            result.addNode(getSameIndividuals(axiom.getObject().asOWLNamedIndividual()));
                        }
                        else {
                            result.addNode(new OWLNamedIndividualNode(axiom.getObject().asOWLNamedIndividual()));
                        }
                    }
                }

            }
        }
        // Could do other stuff like inspecting owl:hasValue restrictions
        return result;
    }

    @Nonnull
    @Override
    public Set<OWLLiteral> getDataPropertyValues(@Nonnull OWLNamedIndividual ind, @Nonnull OWLDataProperty pe) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        Set<OWLLiteral> literals = new HashSet<>();
        Set<OWLDataProperty> superProperties = getSuperDataProperties(pe, false).getFlattened();
        superProperties.addAll(getEquivalentDataProperties(pe).getEntities());
        for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
            for (OWLDataPropertyAssertionAxiom axiom : ontology.getDataPropertyAssertionAxioms(ind)) {
                if (superProperties.contains(axiom.getProperty().asOWLDataProperty())) {
                    literals.add(axiom.getObject());
                }
            }
        }
        return literals;
    }

    @Nonnull
    @Override
    public Node<OWLNamedIndividual> getSameIndividuals(@Nonnull OWLNamedIndividual ind) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        ensurePrepared();
        Set<OWLNamedIndividual> inds = new HashSet<>();
        Set<OWLSameIndividualAxiom> processed = new HashSet<>();
        List<OWLNamedIndividual> stack = new ArrayList<>();
        stack.add(ind);
        while (!stack.isEmpty()) {
            OWLNamedIndividual currentInd = stack.remove(0);
            for (OWLOntology ontology : getRootOntology().getImportsClosure()) {
                for (OWLSameIndividualAxiom axiom : ontology.getSameIndividualAxioms(currentInd)) {
                    if (!processed.contains(axiom)) {
                        processed.add(axiom);
                        for (OWLIndividual i : axiom.getIndividuals()) {
                            if (!i.isAnonymous()) {
                                OWLNamedIndividual namedInd = i.asOWLNamedIndividual();
                                if (!inds.contains(namedInd)) {
                                    inds.add(namedInd);
                                    stack.add(namedInd);
                                }
                            }
                        }
                    }
                }
            }
        }

        return new OWLNamedIndividualNode(inds);
    }

    @Nonnull
    @Override

    public NodeSet<OWLNamedIndividual> getDifferentIndividuals(@Nonnull OWLNamedIndividual ind) throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return new OWLNamedIndividualNodeSet();
    }

    protected OWLDataFactory getDataFactory() {
        return getRootOntology().getOWLOntologyManager().getOWLDataFactory();
    }
    /**
     * @param showBottomNode true if bottom node is to be showed
     */
    public void dumpClassHierarchy(boolean showBottomNode) {
        dumpClassHierarchy(OWLClassNode.getTopNode(), 0, showBottomNode);
    }

    private void dumpClassHierarchy(Node<OWLClass> cls, int level, boolean showBottomNode) {
        if (!showBottomNode && cls.isBottomNode()) {
            return;
        }
        printIndent(level);
        OWLClass representative = cls.getRepresentativeElement();
        System.out.println(getEquivalentClasses(representative));
        for (Node<OWLClass> subCls : getSubClasses(representative, true)) {
            dumpClassHierarchy(subCls, level + 1, showBottomNode);
        }
    }

    /**
     * @param showBottomNode true if bottom node is to be showed
     */
    public void dumpObjectPropertyHierarchy(boolean showBottomNode) {
        dumpObjectPropertyHierarchy(OWLObjectPropertyNode.getTopNode(), 0, showBottomNode);
    }

    private void dumpObjectPropertyHierarchy(Node<OWLObjectPropertyExpression> cls, int level, boolean showBottomNode) {
        if (!showBottomNode && cls.isBottomNode()) {
            return;
        }
        printIndent(level);
        OWLObjectPropertyExpression representative = cls.getRepresentativeElement();
        System.out.println(getEquivalentObjectProperties(representative));
        for (Node<OWLObjectPropertyExpression> subProp : getSubObjectProperties(representative, true)) {
            dumpObjectPropertyHierarchy(subProp, level + 1, showBottomNode);
        }
    }

    /**
     * @param showBottomNode true if bottom node is to be showed
     */
    public void dumpDataPropertyHierarchy(boolean showBottomNode) {
        dumpDataPropertyHierarchy(OWLDataPropertyNode.getTopNode(), 0, showBottomNode);
    }

    private void dumpDataPropertyHierarchy(Node<OWLDataProperty> cls, int level, boolean showBottomNode) {
        if (!showBottomNode && cls.isBottomNode()) {
            return;
        }
        printIndent(level);
        OWLDataProperty representative = cls.getRepresentativeElement();
        System.out.println(getEquivalentDataProperties(representative));
        for (Node<OWLDataProperty> subProp : getSubDataProperties(representative, true)) {
            dumpDataPropertyHierarchy(subProp, level + 1, showBottomNode);
        }
    }

    private void printIndent(int level) {
        for (int i = 0; i < level; i++) {
            System.out.print("    ");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////
    //////  HierarchyInfo
    //////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private abstract class HierarchyInfo<T extends OWLObject> {

        private RawHierarchyProvider<T> rawParentChildProvider;

        /**
         * The entity that always appears in the top node in the hierarchy
         */
        T topEntity;

        /**
         * The entity that always appears as the bottom node in the hierarchy
         */
        T bottomEntity;

        private Set<T> directChildrenOfTopNode = new HashSet<>();

        private Set<T> directParentsOfBottomNode = new HashSet<>();

        private NodeCache<T> nodeCache;

        private String name;

        private int classificationSize;

        public HierarchyInfo(String name, T topEntity, T bottomEntity, RawHierarchyProvider<T> rawParentChildProvider) {
            this.topEntity = topEntity;
            this.bottomEntity = bottomEntity;
            this.nodeCache = new NodeCache<>(this);
            this.rawParentChildProvider = rawParentChildProvider;
            this.name = name;
        }

        public RawHierarchyProvider<T> getRawParentChildProvider() {
            return rawParentChildProvider;
        }

        /**
         * Gets the set of relevant entities from the specified ontology
         * @param ont The ontology
         * @return A set of entities to be "classified"
         */
        protected abstract Set<T> getEntities(OWLOntology ont);

        /**
         * Creates a node for a given set of entities
         * @param cycle The set of entities
         * @return A node
         */
        protected abstract DefaultNode<T> createNode(Set<T> cycle);

        protected abstract DefaultNode<T> createNode();

        /**
         * Gets the set of relevant entities in a particular axiom
         * @param ax The axiom
         * @return The set of relevant entities in the signature of the specified axiom
         */
        protected abstract Set<? extends T> getEntitiesInSignature(OWLAxiom ax);

        Set<T> getEntitiesInSignature(Set<OWLAxiom> axioms) {
            Set<T> result = new HashSet<>();
            for (OWLAxiom ax : axioms) {
                result.addAll(getEntitiesInSignature(ax));
            }
            return result;
        }

        public void computeHierarchy() {
            pm.reasonerTaskStarted("Computing " + name + " hierarchy");
            pm.reasonerTaskBusy();
            nodeCache.clear();
            Map<T, Collection<T>> cache = new HashMap<>();
            Set<T> entities = new HashSet<>();
            for (OWLOntology ont : getRootOntology().getImportsClosure()) {
                entities.addAll(getEntities(ont));
            }
            classificationSize = entities.size();
            pm.reasonerTaskProgressChanged(0, classificationSize);
            updateForSignature(entities, cache);
            pm.reasonerTaskStopped();
        }

        private void updateForSignature(Set<T> signature, Map<T, Collection<T>> cache) {
            HashSet<Set<T>> cyclesResult = new HashSet<>();
            Set<T> processed = new HashSet<>();
            nodeCache.clearTopNode();
            nodeCache.clearBottomNode();
            nodeCache.clearNodes(signature);

            directChildrenOfTopNode.removeAll(signature);

            Set<T> equivTopOrChildrenOfTop = new HashSet<>();
            Set<T> equivBottomOrParentsOfBottom = new HashSet<>();
            for (T entity : signature) {
                if (!processed.contains(entity)) {
                    pm.reasonerTaskProgressChanged(processed.size(), signature.size());
                    tarjan(entity, 0, new Stack<>(), new HashMap<>(), new HashMap<>(), cyclesResult, processed, new HashSet<>(), cache, equivTopOrChildrenOfTop, equivBottomOrParentsOfBottom);
                    throwExceptionIfInterrupted();
                }
            }
            // Store new cycles
            for (Set<T> cycle : cyclesResult) {
                nodeCache.addNode(cycle);
            }

            directChildrenOfTopNode.addAll(equivTopOrChildrenOfTop);
            directChildrenOfTopNode.removeAll(nodeCache.getTopNode().getEntities());

            directParentsOfBottomNode.addAll(equivBottomOrParentsOfBottom);
            directParentsOfBottomNode.removeAll(nodeCache.getBottomNode().getEntities());

            // Now check that each found cycle has a proper parent an child
            for (Set<T> node : cyclesResult) {
                if (!node.contains(topEntity) && !node.contains(bottomEntity)) {
                    boolean childOfTop = true;
                    for (T element : node) {
                        Collection<T> parents = rawParentChildProvider.getParents(element);
                        parents.removeAll(node);
                        parents.removeAll(nodeCache.getTopNode().getEntities());
                        if (!parents.isEmpty()) {
                            childOfTop = false;
                            break;
                        }
                    }
                    if (childOfTop) {
                        directChildrenOfTopNode.addAll(node);
                    }

                    boolean parentOfBottom = true;
                    for (T element : node) {
                        Collection<T> children = rawParentChildProvider.getChildren(element);
                        children.removeAll(node);
                        children.removeAll(nodeCache.getBottomNode().getEntities());
                        if (!children.isEmpty()) {
                            parentOfBottom = false;
                            break;
                        }
                    }
                    if (parentOfBottom) {
                        directParentsOfBottomNode.addAll(node);
                    }
                }

            }

        }

        /**
         * Processes the specified signature that represents the signature of potential changes
         * @param signature The signature
         * @param added added axioms
         * @param removed removed axioms
         */
        @SuppressWarnings("unused")
        public void processChanges(Set<T> signature, Set<OWLAxiom> added, Set<OWLAxiom> removed) {
            updateForSignature(signature, null);
        }

        //////////////////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////////////////

        /**
         * Applies the tarjan algorithm for a given entity.  This computes the cycle that the entity is involved in (if
         * any).
         * @param entity The entity
         * @param index index
         * @param stack stack
         * @param indexMap index map
         * @param lowlinkMap low link map
         * @param result result
         * @param processed processed
         * @param stackEntities stack entities
         * @param cache A cache of children to parents - may be <code>null</code> if no caching is to take place.
         * @param childrenOfTop A set of entities that have a raw parent that is the top entity
         * @param parentsOfBottom A set of entities that have a raw parent that is the bottom entity
         */
        public void tarjan(T entity, int index, Stack<T> stack, Map<T, Integer> indexMap, Map<T, Integer> lowlinkMap, Set<Set<T>> result, Set<T> processed, Set<T> stackEntities, Map<T, Collection<T>> cache, Set<T> childrenOfTop, Set<T> parentsOfBottom) {
            throwExceptionIfInterrupted();
            if (processed.add(entity)) {
                Collection<T> rawChildren = rawParentChildProvider.getChildren(entity);
                if (rawChildren.isEmpty() || rawChildren.contains(bottomEntity)) {
                    parentsOfBottom.add(entity);
                }
            }
            pm.reasonerTaskProgressChanged(processed.size(), classificationSize);
            indexMap.put(entity, index);
            lowlinkMap.put(entity, index);
            index = index + 1;
            stack.push(entity);
            stackEntities.add(entity);

            // Get the raw parents - cache if necessary
            Collection<T> rawParents = null;
            if (cache != null) {
                // We are therefore caching raw parents of children.
                rawParents = cache.get(entity);
                if (rawParents == null) {
                    // Not in cache!
                    rawParents = rawParentChildProvider.getParents(entity);
                    // Note down if our entity is a
                    if (rawParents.isEmpty() || rawParents.contains(topEntity)) {
                        childrenOfTop.add(entity);
                    }
                    cache.put(entity, rawParents);

                }
            }
            else {
                rawParents = rawParentChildProvider.getParents(entity);
                // Note down if our entity is a
                if (rawParents.isEmpty() || rawParents.contains(topEntity)) {
                    childrenOfTop.add(entity);
                }
            }

            for (T superEntity : rawParents) {
                if (!indexMap.containsKey(superEntity)) {
                    tarjan(superEntity, index, stack, indexMap, lowlinkMap, result, processed, stackEntities, cache, childrenOfTop, parentsOfBottom);
                    lowlinkMap.put(entity, Math.min(lowlinkMap.get(entity), lowlinkMap.get(superEntity)));
                }
                else if (stackEntities.contains(superEntity)) {
                    lowlinkMap.put(entity, Math.min(lowlinkMap.get(entity), indexMap.get(superEntity)));
                }
            }
            if (lowlinkMap.get(entity).equals(indexMap.get(entity))) {
                Set<T> scc = new HashSet<>();
                while (true) {
                    T clsPrime = stack.pop();
                    stackEntities.remove(clsPrime);
                    scc.add(clsPrime);
                    if (clsPrime.equals(entity)) {
                        break;
                    }
                }
                if (scc.size() > 1) {
                    // We ADD a cycle
                    result.add(scc);
                }
            }
        }

        //////////////////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////////////////

        public NodeSet<T> getNodeHierarchyChildren(T parent, boolean direct, DefaultNodeSet<T> ns) {
            Node<T> node = nodeCache.getNode(parent);

            if (node.isBottomNode()) {
                return ns;
            }

            Set<T> directChildren = new HashSet<>();
            for (T equiv : node) {
                directChildren.addAll(rawParentChildProvider.getChildren(equiv));
                if(directParentsOfBottomNode.contains(equiv)) {
                    ns.addNode(nodeCache.getBottomNode());
                }
            }
            directChildren.removeAll(node.getEntities());

            if (node.isTopNode()) {
                // Special treatment
                directChildren.addAll(directChildrenOfTopNode);
            }

            for (Node<T> childNode : nodeCache.getNodes(directChildren)) {
                ns.addNode(childNode);
            }

            if (!direct) {
                for (T child : directChildren) {
                    getNodeHierarchyChildren(child, direct, ns);
                }
            }
            return ns;
        }

        public NodeSet<T> getNodeHierarchyParents(T child, boolean direct, DefaultNodeSet<T> ns) {
            Node<T> node = nodeCache.getNode(child);

            if (node.isTopNode()) {
                return ns;
            }

            Set<T> directParents = new HashSet<>();
            for (T equiv : node) {
                directParents.addAll(rawParentChildProvider.getParents(equiv));
                if(directChildrenOfTopNode.contains(equiv)) {
                    ns.addNode(nodeCache.getTopNode());
                }
            }
            directParents.removeAll(node.getEntities());

            if (node.isBottomNode()) {
                // Special treatment
                directParents.addAll(directParentsOfBottomNode);
            }

            for (Node<T> parentNode : nodeCache.getNodes(directParents)) {
                ns.addNode(parentNode);
            }

            if (!direct) {
                for(T parent : directParents) {
                    getNodeHierarchyParents(parent, direct, ns);
                }
            }
            return ns;
        }

        public Node<T> getEquivalents(T element) {
            return nodeCache.getNode(element);
        }
    }

    private static class NodeCache<T extends OWLObject> {

        private HierarchyInfo<T> hierarchyInfo;

        private Node<T> topNode;

        private Node<T> bottomNode;

        private Map<T, Node<T>> map = new HashMap<>();

        protected NodeCache(HierarchyInfo<T> hierarchyInfo) {
            this.hierarchyInfo = hierarchyInfo;
            clearTopNode();
            clearBottomNode();
        }

        public void addNode(Node<T> node) {
            for (T element : node.getEntities()) {
                map.put(element, node);
                if (element.isTopEntity()) {
                    topNode = node;
                }
                else if (element.isBottomEntity()) {
                    bottomNode = node;
                }
            }
        }

        public Set<Node<T>> getNodes(Set<T> elements) {
            Set<Node<T>> result = new HashSet<>();
            for (T element : elements) {
                result.add(getNode(element));
            }
            return result;
        }

        public Node<T> getNode(T containing) {
            Node<T> parentNode = map.get(containing);
            if (parentNode != null) {
                return parentNode;
            }
            else {
                return hierarchyInfo.createNode(Collections.singleton(containing));
            }
        }

        public void addNode(Set<T> elements) {
            addNode(hierarchyInfo.createNode(elements));
        }

        public Node<T> getTopNode() {
            return topNode;
        }

        public Node<T> getBottomNode() {
            return bottomNode;
        }

        public void clearTopNode() {
            removeNode(hierarchyInfo.topEntity);
            topNode = hierarchyInfo.createNode(Collections.singleton(hierarchyInfo.topEntity));
            addNode(topNode);
        }

        public void clearBottomNode() {
            removeNode(hierarchyInfo.bottomEntity);
            bottomNode = hierarchyInfo.createNode(Collections.singleton(hierarchyInfo.bottomEntity));
            addNode(bottomNode);
        }

        public void clearNodes(Set<T> containing) {
            for (T entity : containing) {
                removeNode(entity);
            }
        }

        public void clear() {
            map.clear();
            clearTopNode();
            clearBottomNode();
        }

        public void removeNode(T containing) {
            Node<T> node = map.remove(containing);
            if (node != null) {
                for (T object : node.getEntities()) {
                    map.remove(object);
                }
            }
        }
    }

    private class ClassHierarchyInfo extends HierarchyInfo<OWLClass> {

        public ClassHierarchyInfo() {
            super("class", getDataFactory().getOWLThing(), getDataFactory().getOWLNothing(), new RawClassHierarchyProvider());
        }

        @Override
        protected Set<OWLClass> getEntitiesInSignature(OWLAxiom ax) {
            return ax.getClassesInSignature();
        }

        @Override
        protected DefaultNode<OWLClass> createNode(Set<OWLClass> cycle) {
            return new OWLClassNode(cycle);
        }

        @Override
        protected Set<OWLClass> getEntities(OWLOntology ont) {
            return ont.getClassesInSignature();
        }

        @Override
        protected DefaultNode<OWLClass> createNode() {
            return new OWLClassNode();
        }
    }

    private class ObjectPropertyHierarchyInfo extends HierarchyInfo<OWLObjectPropertyExpression> {

        public ObjectPropertyHierarchyInfo() {
            super("object property", getDataFactory().getOWLTopObjectProperty(), getDataFactory().getOWLBottomObjectProperty(), new RawObjectPropertyHierarchyProvider());
        }

        @Override
        protected Set<OWLObjectPropertyExpression> getEntitiesInSignature(OWLAxiom ax) {
            Set<OWLObjectPropertyExpression> result = new HashSet<>();
            for (OWLObjectProperty property : ax.getObjectPropertiesInSignature()) {
                result.add(property);
                result.add(property.getInverseProperty());
            }
            return result;
        }

        @Override
        protected Set<OWLObjectPropertyExpression> getEntities(OWLOntology ont) {
            Set<OWLObjectPropertyExpression> result = new HashSet<>();
            for (OWLObjectPropertyExpression property : ont.getObjectPropertiesInSignature()) {
                result.add(property);
                result.add(property.getInverseProperty());
            }
            return result;
        }

        @Override
        protected DefaultNode<OWLObjectPropertyExpression> createNode(Set<OWLObjectPropertyExpression> cycle) {
            return new OWLObjectPropertyNode(cycle);
        }

        @Override
        protected DefaultNode<OWLObjectPropertyExpression> createNode() {
            return new OWLObjectPropertyNode();
        }

        @Override
        public void processChanges(Set<OWLObjectPropertyExpression> signature, Set<OWLAxiom> added, Set<OWLAxiom> removed) {
            boolean rebuild = false;
            for (OWLAxiom ax : added) {
                if(ax instanceof OWLObjectPropertyAxiom) {
                    rebuild = true;
                    break;
                }
            }
            if(!rebuild) {
                for(OWLAxiom ax : removed) {
                    if(ax instanceof OWLObjectPropertyAxiom) {
                        rebuild = true;
                        break;
                    }
                }
            }
            if(rebuild) {
                ((RawObjectPropertyHierarchyProvider) getRawParentChildProvider()).rebuild();
            }
            super.processChanges(signature, added, removed);
        }
    }

    private class DataPropertyHierarchyInfo extends HierarchyInfo<OWLDataProperty> {

        public DataPropertyHierarchyInfo() {
            super("data property", getDataFactory().getOWLTopDataProperty(), getDataFactory().getOWLBottomDataProperty(), new RawDataPropertyHierarchyProvider());
        }

        @Override
        protected Set<OWLDataProperty> getEntitiesInSignature(OWLAxiom ax) {
            return ax.getDataPropertiesInSignature();
        }

        @Override
        protected Set<OWLDataProperty> getEntities(OWLOntology ont) {
            return ont.getDataPropertiesInSignature();
        }

        @Override
        protected DefaultNode<OWLDataProperty> createNode(Set<OWLDataProperty> cycle) {
            return new OWLDataPropertyNode(cycle);
        }

        @Override
        protected DefaultNode<OWLDataProperty> createNode() {
            return new OWLDataPropertyNode();
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * An interface for objects who can provide the parents and children of some object.
     * @param <T>
     */
    private interface RawHierarchyProvider<T> {

        /**
         * Gets the parents as asserted.  These parents may also be children (resulting in equivalences).
         * @param child The child whose parents are to be retrieved
         * @return The raw asserted parents of the specified child.  If the child does not have any parents
         *         then the empty set can be returned.
         */
        Collection<T> getParents(T child);

        /**
         * Gets the children as asserted
         * @param parent The parent whose children are to be retrieved
         * @return The raw asserted children of the speicified parent
         */
        Collection<T> getChildren(T parent);

    }

    private class RawClassHierarchyProvider implements RawHierarchyProvider<OWLClass> {
        public RawClassHierarchyProvider() {}
 
        @Override
        public Collection<OWLClass> getParents(OWLClass child) {
            Collection<OWLClass> result = new HashSet<>();
            for (OWLOntology ont : getRootOntology().getImportsClosure()) {
                Stream.concat(
                        ont.getSubClassAxiomsForSubClass(child).stream().map(OWLSubClassOfAxiom::getSuperClass),
                        ont.getEquivalentClassesAxioms(child).stream().flatMap(ax -> ax.getClassExpressionsMinus(child).stream())
                ).forEach(ce -> {
                    if (!ce.isAnonymous()) {
                        result.add(ce.asOWLClass());
                    } else if (ce instanceof OWLObjectIntersectionOf) {
                        OWLObjectIntersectionOf intersectionOf = (OWLObjectIntersectionOf) ce;
                        for (OWLClassExpression conjunct : intersectionOf.asConjunctSet()) {
                            if (!conjunct.isAnonymous()) {
                                result.add(conjunct.asOWLClass());
                            }
                        }
                    }
                });
            }
            return result;
        }

        @Override
        public Collection<OWLClass> getChildren(OWLClass parent) {
            Collection<OWLClass> result = new HashSet<>();
            for (OWLOntology ont : getRootOntology().getImportsClosure()) {
                for (OWLAxiom ax : ont.getReferencingAxioms(parent)) {
                    if (ax instanceof OWLSubClassOfAxiom) {
                        OWLSubClassOfAxiom sca = (OWLSubClassOfAxiom) ax;
                        if (!sca.getSubClass().isAnonymous()) {
                            Set<OWLClassExpression> conjuncts = sca.getSuperClass().asConjunctSet();
                            if (conjuncts.contains(parent)) {
                                result.add(sca.getSubClass().asOWLClass());
                            }
                        }
                    }
                    else if (ax instanceof OWLEquivalentClassesAxiom) {
                        OWLEquivalentClassesAxiom eca = (OWLEquivalentClassesAxiom) ax;
                        for (OWLClassExpression ce : eca.getClassExpressions()) {
                            if (ce.containsConjunct(parent)) {
                                for (OWLClassExpression sub : eca.getClassExpressions()) {
                                    if (!sub.isAnonymous() && !sub.equals(ce)) {
                                        result.add(sub.asOWLClass());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }
    }

    private class RawObjectPropertyHierarchyProvider implements RawHierarchyProvider<OWLObjectPropertyExpression> {

        private OWLObjectPropertyManager propertyManager;

        private Map<OWLObjectPropertyExpression, Set<OWLObjectPropertyExpression>> sub2Super;

        private Map<OWLObjectPropertyExpression, Set<OWLObjectPropertyExpression>> super2Sub;

        public RawObjectPropertyHierarchyProvider() {
            rebuild();
        }

        public void rebuild() {
            propertyManager = new OWLObjectPropertyManager(getRootOntology().getOWLOntologyManager(), getRootOntology());
            sub2Super = propertyManager.getPropertyHierarchy();
            super2Sub = new HashMap<>();
            for(Map.Entry<OWLObjectPropertyExpression, Set<OWLObjectPropertyExpression>> entry : sub2Super.entrySet()) {
                for(OWLObjectPropertyExpression superProp : entry.getValue()) {
                    super2Sub
                            .computeIfAbsent(superProp, k -> new HashSet<>())
                            .add(entry.getKey());
                }
            }
        }

        @Override
        public Collection<OWLObjectPropertyExpression> getParents(OWLObjectPropertyExpression child) {
            if(child.isBottomEntity()) {
                return Collections.emptySet();
            }
            Set<OWLObjectPropertyExpression> propertyExpressions = sub2Super.get(child);
            if(propertyExpressions == null) {
                return Collections.emptySet();
            }
            else {
                return new HashSet<>(propertyExpressions);
            }

        }

        @Override
        public Collection<OWLObjectPropertyExpression> getChildren(OWLObjectPropertyExpression parent) {
            if(parent.isTopEntity()) {
                return Collections.emptySet();
            }
            Set<OWLObjectPropertyExpression> propertyExpressions = super2Sub.get(parent);
            if(propertyExpressions == null) {
                return Collections.emptySet();
            }
            else {
                return new HashSet<>(propertyExpressions);
            }

        }
    }

    private class RawDataPropertyHierarchyProvider implements RawHierarchyProvider<OWLDataProperty> {
        public RawDataPropertyHierarchyProvider() {}
        @Override
        public Collection<OWLDataProperty> getParents(OWLDataProperty child) {
            Set<OWLDataProperty> properties = new HashSet<>();
            for (OWLDataPropertyExpression prop : EntitySearcher.getSuperProperties(child, getRootOntology().getImportsClosure())) {
                properties.add(prop.asOWLDataProperty());
            }
            return properties;
        }

        @Override
        public Collection<OWLDataProperty> getChildren(OWLDataProperty parent) {
            Set<OWLDataProperty> properties = new HashSet<>();
            for (OWLDataPropertyExpression prop : EntitySearcher.getSubProperties(parent, getRootOntology().getImportsClosure())) {
                properties.add(prop.asOWLDataProperty());
            }
            return properties;
        }
    }

    public static void main(String[] args) throws Exception {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology schema = //man.loadOntology(IRI.create("http://downloads.dbpedia.org/2016-10/dbpedia_2016-10.nt"));
        man.loadOntology(IRI.create("file://" + System.getProperty("java.io.tmpdir") + "/merged.ttl"));

        OWLOntologyKnowledgeSource sampleKS = new OWLAPIOntology(schema);
        sampleKS.init();


        final long start = System.currentTimeMillis();

        OWLAPIReasoner baseReasoner = new OWLAPIReasoner(sampleKS);
        baseReasoner.setReasonerImplementation(ReasonerImplementation.STRUCTURAL);
        baseReasoner.init();

        ClosedWorldReasoner reasoner = new ClosedWorldReasoner(baseReasoner);
        reasoner.init();

        System.out.println(PeriodFormat.getDefault().print(new Period(System.currentTimeMillis() - start)));
    }

}
