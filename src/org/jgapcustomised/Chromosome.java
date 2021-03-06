/*
 * Copyright 2001-2003 Neil Rotstan Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of JGAP.
 * 
 * JGAP is free software; you can redistribute it and/or modify it under the terms of the GNU
 * Lesser Public License as published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * JGAP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License along with JGAP; if not,
 * write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * Modified on Feb 3, 2003 by Philip Tucker
 */
package org.jgapcustomised;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import com.ojcoleman.ahni.evaluation.novelty.Behaviour;

/**
 * Chromosomes represent potential solutions and consist of a fixed-length collection of genes. Each gene represents a
 * discrete part of the solution. Each gene in the Chromosome may be backed by a different concrete implementation of
 * the Gene interface, but all genes with the same innovation ID must share the same concrete implementation across
 * Chromosomes within a single population (genotype).
 */
public class Chromosome implements Comparable, Serializable {
	/**
	 * default ID
	 */
	public final static Long DEFAULT_ID = new Long(-1);

	private Long m_id = DEFAULT_ID;

	private String m_idString;

	/**
	 * Genetic material contained in this chromosome.
	 */
	private ChromosomeMaterial m_material = null;

	private SortedSet m_alleles = null;

	/**
	 * Keeps track of whether or not this Chromosome has been selected by the natural selector to move on to the next
	 * generation.
	 */
	protected boolean m_isSelectedForNextGeneration = false;

	/**
	 * Stores the fitness value(s) of this Chromosome as determined by the active fitness function(s). A value of
	 * Double.NaN indicates that a fitness value has not yet been set.
	 */
	protected double[] m_fitnessValue;

	/**
	 * Stores the overall fitness value of this Chromosome, either as determined by the active fitness function or as
	 * determined by the {@link org.jgapcustomised.NaturalSelector} based on the fitness values over all objectives. A
	 * value of Double.NaN indicates that a fitness value has not yet been set.
	 */
	protected double m_overallFitnessValue = Double.NaN;

	protected double m_performanceValue = Double.NaN;
	
	protected boolean evaluationDataStable = false;

	protected Species m_specie = null;

	public boolean isElite = false;
	
	public int rank = 0;

	/**
	 * May be used by implementations of novelty search.
	 * 
	 * @see com.ojcoleman.ahni.evaluation.novelty.NoveltySearch
	 */
	public double novelty = 0;

	/**
	 * May be used by implementations of novelty search. An array is used so that multiple behaviours may be defined.
	 * 
	 * @see com.ojcoleman.ahni.evaluation.novelty.NoveltySearch
	 */
	public Behaviour[] behaviours;

	/**
	 * Used by selection algorithms to store a value representing how crowded the fitness space is relative to other
	 * individuals with the same rank (on the same Pareto front), most useful for multi-objective selection algorithms
	 * such as NSGA-II.
	 */
	public double crowdingDistance;

	/**
	 * ctor for hibernate
	 */
	private Chromosome() {
		this(1, 0);
	}

	/**
	 * ctor for hibernate
	 */
	private Chromosome(int objectiveCount, int behaviourCount) {
		m_material = new ChromosomeMaterial();
		m_fitnessValue = new double[objectiveCount];
		Arrays.fill(m_fitnessValue, Double.NaN);
		if (behaviourCount > 0) {
			behaviours = new Behaviour[behaviourCount];
		}
	}

	/**
	 * this should only be called when a chromosome is being created from persistence; otherwise, the ID should be
	 * generated by <code>a_activeConfiguration</code>.
	 * 
	 * @param a_material Genetic material to be contained within this Chromosome instance.
	 * @param an_id unique ID of new chromosome
	 */
	public Chromosome(ChromosomeMaterial a_material, Long an_id, int objectiveCount, int behaviourCount) {
		// Sanity checks: make sure the parameters are all valid.
		if (a_material == null)
			throw new IllegalArgumentException("Chromosome material can't be null.");

		setId(an_id);
		m_material = a_material;
		m_alleles = Collections.unmodifiableSortedSet(m_material.getAlleles());
		associateAllelesWithChromosome();
		m_fitnessValue = new double[objectiveCount];
		Arrays.fill(m_fitnessValue, Double.NaN);
		if (behaviourCount > 0) {
			behaviours = new Behaviour[behaviourCount];
		}
	}

	private void associateAllelesWithChromosome() {
		Iterator it = m_alleles.iterator();
		while (it.hasNext()) {
			Allele allele = (Allele) it.next();
			allele.setChromosome(this);
		}
	}

	/**
	 * Calculates compatibility distance between this and <code>target</code> according to <a
	 * href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf">NEAT </a> speciation methodology. It is generic
	 * enough that the alleles do not have to be nodes and connections.
	 * 
	 * @param target
	 * @param parms
	 * @return distance between this object and <code>target</code>
	 * @see ChromosomeMaterial#distance(ChromosomeMaterial, SpeciationParms)
	 */
	public double distance(Chromosome target, SpeciationParms parms) {
		return m_material.distance(target.m_material, parms);
	}

	/**
	 * @return Long unique identifier for chromosome; useful for <code>hashCode()</code> and persistence
	 */
	public Long getId() {
		return m_id;
	}

	/**
	 * for hibernate
	 * 
	 * @param id
	 */
	private void setId(Long id) {
		m_id = id;
		m_idString = "Chromosome " + m_id;
	}

	/**
	 * Returns the size of this Chromosome (the number of alleles it contains). A Chromosome's size is constant and will
	 * never change.
	 * 
	 * @return The number of alleles contained within this Chromosome instance.
	 */
	public int size() {
		return m_alleles.size();
	}

	/**
	 * @return clone with primary parent ID of this chromosome and the same genetic material.
	 */
	public ChromosomeMaterial cloneMaterial() {
		return m_material.clone(getId());
	}

	/**
	 * @return SortedSet alleles, sorted by innovation ID
	 */
	public SortedSet getAlleles() {
		return m_alleles;
	}

	/**
	 * @param alleleToMatch
	 * @return Gene gene with same innovation ID as <code>geneToMatch</code, or <code>null</code> if none match
	 */
	public Allele findMatchingGene(Allele alleleToMatch) {
		Iterator iter = m_alleles.iterator();
		while (iter.hasNext()) {
			Allele allele = (Allele) iter.next();
			if (allele.equals(alleleToMatch))
				return allele;
		}
		return null;
	}

	/**
	 * Returns the overall fitness value of this Chromosome, either as determined by the active fitness function or as
	 * determined by the {@link org.jgapcustomised.NaturalSelector} based on the fitness values over all objectives.
	 * Fitness values are in the range [0, 1], however the value Double.NaN indicates that a fitness hasn't been set
	 * yet.
	 */
	public double getFitnessValue() {
		return m_overallFitnessValue;
	}

	/**
	 * Returns the fitness value of this Chromosome for the given objective, as determined by the active fitness
	 * function. Fitness values are in the range [0, 1], however the value Double.NaN indicates that a fitness hasn't
	 * been set yet.
	 */
	public double getFitnessValue(int objective) {
		return m_fitnessValue[objective];
	}

	/**
	 * Returns a reference to the array of fitness values for this Chromosome for each objective, as determined by the
	 * active fitness function. Fitness values are in the range [0, 1], however the value Double.NaN indicates that a
	 * fitness hasn't been set yet.
	 */
	public double[] getFitnessValues() {
		return m_fitnessValue;
	}

	/**
	 * @return int fitness value adjusted for fitness sharing according to <a
	 *         href="http://nn.cs.utexas.edu/downloads/papers/stanley.ec02.pdf>NEAT </a> paradigm.
	 */
	public double getSpeciatedFitnessValue() {
		if (m_specie == null)
			return getFitnessValue();
		return m_specie.getChromosomeSpeciatedFitnessValue(this);
	}

	/**
	 * Sets the overall fitness value of this Chromosome. This method is for use by bulk fitness functions and should
	 * not be invoked from anything else. This is the raw fitness value, before species fitness sharing. Fitness values
	 * must be in the range [0, 1].
	 * 
	 * @param a_newFitnessValue the fitness of this Chromosome.
	 */
	public void setFitnessValue(double a_newFitnessValue) {
		if (a_newFitnessValue < 0 || a_newFitnessValue > 1) {
			throw new IllegalArgumentException("Fitness values must be in the range [0, 1], or have value Double.NaN.");
		}
		m_overallFitnessValue = a_newFitnessValue;
	}

	/**
	 * Sets the fitness value of this Chromosome for the given objective. This method is for use by bulk fitness
	 * functions and should not be invoked from anything else. Fitness values must be in the range [0, 1].
	 * 
	 * @param a_newFitnessValue the fitness of this Chromosome.
	 */
	public void setFitnessValue(double a_newFitnessValue, int objective) {
		if (a_newFitnessValue < 0 || a_newFitnessValue > 1) {
			throw new IllegalArgumentException("Fitness values must be in the range [0, 1], or have value Double.NaN, but " + a_newFitnessValue + " was given for objective " + objective + ".");
		}
		m_fitnessValue[objective] = a_newFitnessValue;
	}

	/**
	 * Returns the performance value of this Chromosome as determined by the active fitness function. Performance values
	 * are in the range [0, 1], however the value Double.NaN indicates that a fitness hasn't been set yet.
	 */
	public double getPerformanceValue() {
		return m_performanceValue;
	}

	/**
	 * Sets the performance value of this Chromosome. This method is for use by bulk fitness functions and should not be
	 * invoked from anything else. Performance values must be in the range [0, 1].
	 * 
	 * @param aPerformanceValue the fitness of this Chromosome.
	 */
	public void setPerformanceValue(double aPerformanceValue) {
		if (aPerformanceValue < 0 || aPerformanceValue > 1) {
			throw new IllegalArgumentException("Performance values must be in the range [0, 1], but " + aPerformanceValue + " was given.");
		}
		this.m_performanceValue = aPerformanceValue;
	}

	/**
	 * Resets the performance value of this Chromosome to Double.NaN.
	 */
	public void resetPerformanceValue() {
		if (!isEvaluationDataStable()) {
			m_performanceValue = Double.NaN;
		}
	}
	
	/**
	 * Resets the fitness value(s) of this Chromosome to Double.NaN.
	 */
	public void resetFitnessValues() {
		if (!isEvaluationDataStable()) {
			m_overallFitnessValue = Double.NaN;
			for (int i = 0; i < m_fitnessValue.length; i++) {
				m_fitnessValue[i] = Double.NaN;
			}
		}
	}

	
	/**
	 * Resets the performance, fitness value(s) and recorded novelty behaviours(s) of this Chromosome.
	 */
	public void resetEvaluationData() {
		if (!isEvaluationDataStable()) {
			resetPerformanceValue();
			resetFitnessValues();
			if (behaviours != null) {
				Arrays.fill(behaviours, null);
			}
		}
	}
	
	/**
	 * Indicate that the evaluation data (e.g. fitness, performance, behaviours) is stable: it won't change in future evaluations.
	 * This only has an effect if the fitness function pays attention to this value, and the effect that it has may vary between implementations.
	 */
	public void setEvaluationDataStable() {
		evaluationDataStable = true;
	}
	
	public boolean isEvaluationDataStable() {
		return evaluationDataStable;
	}
	
	/**
	 * Returns a string representation of this Chromosome, useful for some display purposes.
	 * 
	 * @return A string representation of this Chromosome.
	 */
	public String toString() {
		return m_idString;
	}

	/**
	 * Compares this Chromosome against the specified object. The result is true if and the argument is an instance of
	 * the Chromosome class and has the same ID.
	 * 
	 * @param other The object to compare against.
	 * @return true if the objects are the same, false otherwise.
	 */
	public boolean equals(Object other) {
		return compareTo(other) == 0;
	}

	/**
	 * Retrieve a hash code for this Chromosome.
	 * 
	 * @return the hash code of this Chromosome.
	 */
	public int hashCode() {
		return m_id.hashCode();
	}

	/**
	 * Compares the given Chromosome to this Chromosome by their ID.
	 * 
	 * @param o The Chromosome against which to compare this chromosome.
	 * @return a negative number if this chromosome is "less than" the given chromosome, zero if they are equal to each
	 *         other, and a positive number if this chromosome is "greater than" the given chromosome.
	 */
	public int compareTo(Object o) {
		Chromosome other = (Chromosome) o;
		return m_id.compareTo(other.m_id);
		// return m_material.compareTo(other.m_material);
	}

	/**
	 * Compares this Chromosome against the specified object. The result is true if and the argument is an instance of
	 * the Chromosome class and has a set of genes with equal values (eg connection weight, activation type) to this
	 * one.
	 * 
	 * @param c2 The object to compare against.
	 * @return true if the objects are the same, false otherwise.
	 */
	public boolean isEquivalent(Chromosome c2) {
		return m_material.isEquivalent(c2.m_material);
	}

	/**
	 * Sets whether this Chromosome has been selected by the natural selector to continue to the next generation.
	 * 
	 * @param a_isSelected true if this Chromosome has been selected, false otherwise.
	 */
	public void setIsSelectedForNextGeneration(boolean a_isSelected) {
		m_isSelectedForNextGeneration = a_isSelected;
	}

	/**
	 * Retrieves whether this Chromosome has been selected by the natural selector to continue to the next generation.
	 * 
	 * @return true if this Chromosome has been selected, false otherwise.
	 */
	public boolean isSelectedForNextGeneration() {
		return m_isSelectedForNextGeneration;
	}

	/**
	 * should only be called from Species; assigns this chromosome to <code>aSpecie</code>; throws exception if
	 * chromosome is added to a specie twice
	 * 
	 * @param aSpecie
	 */
	public void setSpecie(Species aSpecie) {
		if (m_specie != null)
			throw new IllegalStateException("chromosome can't be added to " + aSpecie + ", already a member of specie " + m_specie);
		m_specie = aSpecie;
	}

	/**
	 * Resets (clears) the specie this chromose belongs to.
	 */
	public void resetSpecie() {
		m_specie = null;
	}
	
	/**
	 * for hibernate
	 * 
	 * @param id
	 */
	private void setPrimaryParentId(Long id) {
		m_material.setPrimaryParentId(id);
	}

	/**
	 * for hibernate
	 * 
	 * @param id
	 */
	private void setSecondaryParentId(Long id) {
		m_material.setSecondaryParentId(id);
	}

	/**
	 * @return this chromosome's specie
	 */
	public Species getSpecie() {
		return m_specie;
	}

	/**
	 * @return primary parent ID; this is the dominant parent for chromosomes spawned by crossover, and the only parent
	 *         for chromosomes spawned by cloning
	 */
	public Long getPrimaryParentId() {
		return m_material.getPrimaryParentId();
	}

	/**
	 * @return secondary parent ID; this is the recessive parent for chromosomes spawned by crossover, and null for
	 *         chromosomes spawned by cloning
	 */
	public Long getSecondaryParentId() {
		return m_material.getSecondaryParentId();
	}

	/**
	 * for hibernate
	 * 
	 * @param aAlleles
	 */
	private void setAlleles(SortedSet aAlleles) {
		m_material.setAlleles(aAlleles);
		m_alleles = Collections.unmodifiableSortedSet(aAlleles);
		associateAllelesWithChromosome();
	}

	public int getObjectiveCount() {
		return m_fitnessValue.length;
	}

	public int getNoveltyObjectiveCount() {
		return behaviours == null ? 0 : behaviours.length;
	}

	/**
	 * Checks whether this individual dominates the specified other individual, i.e. it is at least as good as the other
	 * one in all objectives and for at least one objective it is better (i.e. higher fitness value).
	 * 
	 * This code is based on JNSGA2 by Joachim Melcher, Institut AIFB, Universitaet Karlsruhe (TH), Germany
	 * http://sourceforge.net/projects/jnsga2
	 * 
	 * @param otherChromosome other individual
	 * @return <code>true</code> iff this individual dominates the specified one
	 */
	public boolean dominates(Chromosome otherChromosome) {
		/* check special cases: at least one fitness value is 'NaN' (not a number) */
		boolean hasThisNaN = false;
		boolean hasOtherNaN = false;

		for (int i = 0; i < getObjectiveCount(); i++) {
			if (new Double(getFitnessValue(i)).equals(Double.NaN)) {
				hasThisNaN = true;
			}
			if (new Double(otherChromosome.getFitnessValue(i)).equals(Double.NaN)) {
				hasOtherNaN = true;
			}
		}

		// If it looks like we might not be using multi-objective, try using overall/old fitness values.
		if (getObjectiveCount() == 1 && hasThisNaN && hasOtherNaN) {
			return getFitnessValue() > otherChromosome.getFitnessValue();
		}

		if (hasThisNaN) {
			return false;
		}

		if (!hasThisNaN && hasOtherNaN) {
			return true;
		}

		// Both individuals have no 'NaN'
		boolean atLeastOneObjectiveBetter = false;

		for (int i = 0; i < getObjectiveCount(); i++) {
			if (getFitnessValue(i) < otherChromosome.getFitnessValue(i)) {
				return false;
			}
			if (getFitnessValue(i) > otherChromosome.getFitnessValue(i)) {
				atLeastOneObjectiveBetter = true;
			}
		}

		return atLeastOneObjectiveBetter;
	}
	
	public ChromosomeMaterial getMaterial() {
		return m_material;
	}
}
