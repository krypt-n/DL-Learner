/**
 * Copyright (C) 2007, Jens Lehmann
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
 *
 */
package org.dllearner.test;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import org.dllearner.algorithms.RandomGuesser;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.ComponentManager;
import org.dllearner.core.KnowledgeSource;
import org.dllearner.core.LearningAlgorithm;
import org.dllearner.core.LearningProblem;
import org.dllearner.core.LearningProblemUnsupportedException;
import org.dllearner.core.ReasonerComponent;
import org.dllearner.core.ReasoningService;
import org.dllearner.kb.OWLFile;
import org.dllearner.learningproblems.PosNegDefinitionLP;
import org.dllearner.reasoning.DIGReasoner;

/**
 * Test for component based design.
 * 
 * @author Jens Lehmann
 * 
 */
public class ComponentTest {

	/**
	 * @param args
	 * @throws ComponentInitException 
	 */
	public static void main(String[] args) throws ComponentInitException {
		
		// get singleton instance of component manager
		ComponentManager cm = ComponentManager.getInstance();
		
		// create knowledge source
		KnowledgeSource source = cm.knowledgeSource(OWLFile.class);
		String example = "examples/family/father.owl";
		cm.applyConfigEntry(source, "url", new File(example).toURI().toString());
		source.init();
		
		// create DIG reasoning service with standard settings
		ReasonerComponent reasoner = cm.reasoner(DIGReasoner.class, source);
		// ReasoningService rs = cm.reasoningService(DIGReasonerNew.class, source);
		reasoner.init();
		ReasoningService rs = cm.reasoningService(reasoner);
		
		// create a learning problem and set positive and negative examples
		LearningProblem lp = cm.learningProblem(PosNegDefinitionLP.class, rs);
		Set<String> positiveExamples = new TreeSet<String>();
		positiveExamples.add("http://example.com/father#stefan");
		positiveExamples.add("http://example.com/father#markus");
		positiveExamples.add("http://example.com/father#martin");
		Set<String> negativeExamples = new TreeSet<String>();
		negativeExamples.add("http://example.com/father#heinz");
		negativeExamples.add("http://example.com/father#anna");
		negativeExamples.add("http://example.com/father#michelle");
		cm.applyConfigEntry(lp, "positiveExamples", positiveExamples);
		cm.applyConfigEntry(lp, "negativeExamples", negativeExamples);
		lp.init();
		
		// create the learning algorithm
		LearningAlgorithm la = null;
		try {
			la = cm.learningAlgorithm(RandomGuesser.class, lp, rs);
		} catch (LearningProblemUnsupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		cm.applyConfigEntry(la, "numberOfTrees", 100);
		cm.applyConfigEntry(la, "maxDepth", 5);
		la.init();
		
		// start the algorithm and print the best concept found
		la.start();
		System.out.println(la.getBestSolution());
	}

}
