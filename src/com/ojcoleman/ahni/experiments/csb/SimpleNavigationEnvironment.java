package com.ojcoleman.ahni.experiments.csb;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.Vector;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.log4j.Logger;

import com.ojcoleman.ahni.hyperneat.Properties;
import com.ojcoleman.ahni.util.ArrayUtil;

/**
 * <p>
 * Implements an environment that essentially represents a simple navigation like task. The agent perceives the current
 * state as is (no transformation is performed between the environments current state and the agents perception of it).
 * The agent can directly move around within the Cartesian space represented by the environment state space: the agents
 * output is interpreted as a motion vector which changes the current state in a linear additive way. The reward signal,
 * which is a function of the Euclidean distance from the current environment state to the goal state, is provided
 * directly to the agent.
 * </p>
 * <p>
 * Environments can optionally have (hyper-)spherical obstacles added at random locations (increasing the environment
 * difficulty increases the number of obstacles). The agent cannot directly perceive the obstacles. If the environment
 * size (dimensionality) is 2 then the images generated by RLContinuousStateBased will include the obstacle locations.
 * </p>
 */
public class SimpleNavigationEnvironment extends Environment {
	private static Logger logger = Logger.getLogger(SimpleNavigationEnvironment.class);

	/**
	 * The initial number of obstacles that should be included. Default is 0.
	 */
	public static final String OBSTACLE_COUNT_INITIAL = "fitness.function.rlcss.obstacles.initial";
	/**
	 * The amount to increase the obstacle count when environments with the current value have been sufficiently
	 * mastered (see {@link RLContinuousStateBased#DIFFICULTY_INCREASE_PERFORMANCE}. If the value is followed by an "x"
	 * then the value is considered a factor (and so should be > 1). Default is 1.
	 */
	public static final String OBSTACLE_COUNT_INCREASE_DELTA = "fitness.function.rlcss.obstacles.delta";
	/**
	 * The maximum amount to increase the obstacle count to. Default is lots.
	 */
	public static final String OBSTACLE_COUNT_MAX = "fitness.function.rlcss.obstacles.maximum";

	Random random;
	protected int obstacleCount;
	Obstacle[] obstacle;
	int requiredSteps = 0;
	double maxStepSize = 0.1;
	static int noveltyEnvironmentsDone = 0;

	@Override
	public void init(Properties props) {
		super.init(props);

		obstacleCount = props.getIntProperty(OBSTACLE_COUNT_INITIAL, 0);
	}

	@Override
	public void setUp(int id) {
		this.id = id;
		random = props.getEvolver().getConfig().getRandomGenerator();

		startState = new ArrayRealVector(size);
		goalState = new ArrayRealVector(size);
		// If environment is for regular fitness testing.
		if (id >= 0) {
			for (int i = 0; i < size; i++) {
				// Start state is in randomly selected corner.
				// startState.setEntry(i, (random.nextBoolean() ? 0 : 0.9) + random.nextDouble() * 0.1);

				// Start state is anywhere.
				// startState.setEntry(i, random.nextDouble());

				// middle (if always the same start state then agent can't determine environment instance from initial state)
				startState.setEntry(i, 0.5);
			}

			if (size == 2) {
				// Ensure even distribution
				double ad = 2 * Math.PI / rlcsb.getEnvironmentCount();
				double a = ad * (id % rlcsb.getEnvironmentCount());
				//a += random.nextDouble() * ad * 0.4;
				goalState.setEntry(0, 0.5 + Math.cos(a)/2.01);
	            goalState.setEntry(1, 0.5 + Math.sin(a)/2.01);
	            //System.err.println((int) (goalState.getEntry(0) * 1000) + "," + (int) (goalState.getEntry(1) * 1000));
			}
			else {
				// Goal state is in randomly selected location with minimum distance from start state.
				do {
					for (int i = 0; i < size; i++) {
						goalState.setEntry(i, random.nextDouble());
					}
				} while (goalState.getDistance(startState) < 0.4 || goalState.getDistance(startState) > 0.5);
			}
			
			// Goal state is in corner according to id (iterate over all possible corners).
			/*
			 * String corners = Integer.toBinaryString(id); for (int i = 0, ci = corners.length() - 1; i < size; i++,
			 * ci--) { // Goal state is in corner according to id (iterate over all possible corners).
			 * goalState.setEntry(i, (ci < 0 || corners.charAt(ci) == '0' ? 0 : 0.9) + random.nextDouble() * 0.1); }
			 */
		} else { // Environment is for novelty testing.
			int granularity = 3;
			double stepSize = 1.0 / (granularity - 1);
			MultidimensionalCounter mc = new MultidimensionalCounter(ArrayUtil.newArray(size, granularity));
			int[] pos = mc.getCounts(-id - 1);
			for (int d = 0; d < size; d++) {
				startState.setEntry(d, 0.5);
				goalState.setEntry(d, pos[d] * stepSize);
			}
			double d = goalState.getDistance(startState);
			if (d > 0.5) {
				// Make sure distance from start state is 0.5.
				goalState.mapSubtractToSelf(0.5).mapMultiplyToSelf(0.5/d).mapAddToSelf(0.5);
			}
			
			/*String corners = Integer.toBinaryString(-id - 1);
			for (int i = 0, ci = corners.length() - 1; i < size; i++, ci--) {
				// Start state is roughly in middle.
				startState.setEntry(i, 0.4 + random.nextDouble() * 0.2);
				// Goal state is in corner according to id (iterate over all possible corners).
				goalState.setEntry(i, (ci < 0 || corners.charAt(ci) == '0' ? 0 : 0.9) + random.nextDouble() * 0.1);
			}*/
		}

		obstacle = new Obstacle[obstacleCount];
		double path = 0, minPath = findPath();
		double obstSize = 1;
		for (int o = 0; o < obstacleCount; o++) {
			boolean validPlacement = false;
			int attempts = 0;
			do {
				obstacle[o] = new Obstacle(obstSize);
				validPlacement = !obstacle[o].collision(startState) && !obstacle[o].collision(goalState);
				if (validPlacement) {
					path = findPath();
					// If this obstacle placement will completely block the goal, or it doesn't increase the path length from the bare minimum.
					if (path == -1 || path <= minPath) {
						validPlacement = false;
					}
				}
				if (!validPlacement) {
					// Keep shrinking radius until we can make it fit.
					obstSize *= 0.95;
					attempts++;
				}

				// If we can't seem to find anywhere to put this obstacle, start all over again.
				if (attempts == 1000) {
					logger.warn("Couldn't seem to find anywhere to put obstacle " + o + ", restarting obstacle placements.");
					o = -1;
					obstSize = 1;
					break;
				}
			} while (!validPlacement);
		}
		if (obstacleCount == 0) {
			path = findPath();
		}
		requiredSteps = (int) Math.round((path * 1.1) / maxStepSize) + 1;
		if (props.getIntProperty(RLContinuousStateBased.TRIAL_COUNT) == 1) {
			// The agent needs to try different directions to see which is the right one in the same trial
			// allow some extra time for this (2 directions in each axis).
			requiredSteps += 2 * size;
		}
	}

	@Override
	public double updateStateAndOutput(ArrayRealVector state, double[] input, double[] output) {
		assert !Double.isNaN(ArrayUtil.sum(input)) : "updateStateAndOutput(): input array contains NaN: " + Arrays.toString(input);
		assert !state.isNaN() : "updateStateAndOutput(): state contains NaN: " + state;
		
		// Update state given input.
		ArrayRealVector inputT = new ArrayRealVector(input);
		// inputT.mapDivideToSelf(Math.sqrt(input.length));
		if (inputT.getNorm() > 1) {
			inputT.unitize();
		}
		ArrayRealVector newState = state.copy();
		double[] newStateData = newState.getDataRef();
		double[] stateData = state.getDataRef();
		for (int i = 0; i < stateData.length; i++) {
			newStateData[i] = Math.min(1, Math.max(0, stateData[i] + inputT.getEntry(i) * maxStepSize));
		}

		// Determine if a collision has occurred with any obstacle.
		for (int o = 0; o < obstacleCount; o++) {
			// If collision occurs then disallow the move (state does not change).
			if (obstacle[o].collision(newState)) {
				return getOutputForState(state, output);
			}
		}

		// Update state if no collision occurred.
		state.setSubVector(0, newState);

		// Update output given new state.
		return getOutputForState(state, output);
	}

	@Override
	public double getOutputForState(ArrayRealVector state, double[] output) {
		assert !state.isNaN() : "getOutputForState(): state contains NaN: " + state;
		assert !Double.isNaN(ArrayUtil.sum(output)) : "getOutputForState(): input array contains NaN: " + Arrays.toString(output);
		
		// Update output given new state.
		System.arraycopy(state.getDataRef(), 0, output, 0, state.getDimension());

		// for (int i = 0; i < output.length - 1; i++) {
		// output[i] = state.getEntry(i) - goalState.getEntry(i);
		// }

		output[output.length - 1] = getRewardForState(state);
		
		assert !Double.isNaN(ArrayUtil.sum(output)) : "getOutputForState(): input array contains NaN: " + Arrays.toString(output);
		
		return getPerformanceForState(state);
	}

	@Override
	public double getRewardForState(ArrayRealVector state) {
		double d = state.getL1Distance(goalState) / size;
		return 1 - d;
		// return Math.pow(1-d, 2);
		//return d < maxStepSize ? 1 : (1 - d) * 0.5;
	}

	@Override
	public double getPerformanceForState(ArrayRealVector state) {
		double d = state.getL1Distance(goalState) / size;
		// return 1 - d;
		return d < maxStepSize ? 1 : (1-d)*0.1;
	}

	/*
	 * Returns the length (in terms of minimum steps) of the approximate shortest path through the environment, or -1 if
	 * no path was found. The returned length represents an upper bound due to the path following a uniform grid within
	 * the space (taxicab geometry). TODO Use sampling method when size (number of dimensions) > 5 or so (also dependent
	 * on maxStepSize but to a lesser extent, could base decision on pointCount).
	 */
	protected double findPath() {
		if (obstacleCount == 0) {
			return startState.getDistance(goalState);
		}

		int granularity = (int) Math.ceil(1 / maxStepSize) + 1;
		double stepSize = 1.0 / (granularity - 1);
		int pointCount = (int) Math.pow(granularity, size);
		BitSet obstacleOccludes = new BitSet(pointCount);
		MultidimensionalCounter mc = new MultidimensionalCounter(ArrayUtil.newArray(size, granularity));

		// Determine which points are occluded by obstacles. This is probably not a particularly efficient
		// implementation.
		ArrayRealVector pointARV = new ArrayRealVector(size);
		double[] point = pointARV.getDataRef();
		MultidimensionalCounter.Iterator mci = mc.iterator();
		// For each point in the grid.
		for (int i = 0; i < pointCount; i++) {
			int idx = mci.next();
			for (int d = 0; d < size; d++)
				point[d] = mci.getCount(d) * stepSize;

			for (int o = 0; o < obstacleCount; o++) {
				if (obstacle[o] != null && obstacle[o].collision(pointARV)) {
					obstacleOccludes.set(idx);
					break;
				}
			}
		}

		// Seed start and goal covered and front sets with corresponding closest points in grid.
		BitSet startCovered = new BitSet(pointCount); // Record which points have been covered in search emanating from
														// start state.
		Vector<Point> startFront = new Vector<Point>(); // List of indices corresponding to current search front
														// emanating from start state.
		int[] sfIndices = new int[size];
		for (int d = 0; d < size; d++)
			sfIndices[d] = (int) Math.floor(startState.getEntry(d) * granularity);
		int idx = mc.getCount(sfIndices);
		startCovered.set(idx);
		startFront.add(new Point(sfIndices, idx));

		BitSet goalCovered = new BitSet(pointCount); // Record which points have been covered in search emanating from
														// goal state.
		Vector<Point> goalFront = new Vector<Point>(); // List of indices corresponding to current search front
														// emanating from goal state.
		int[] gfIndices = new int[size];
		for (int d = 0; d < size; d++)
			gfIndices[d] = (int) Math.floor(goalState.getEntry(d) * granularity);
		idx = mc.getCount(gfIndices);
		goalCovered.set(idx);
		goalFront.add(new Point(gfIndices, idx));

		// Iteratively expand start and goal fronts until they meet or can no longer be expanded.
		Vector<Point> startFrontNext = new Vector<Point>(), goalFrontNext = new Vector<Point>();
		int[] indexOffsets = new int[size];
		for (int d = 0; d < size; d++)
			indexOffsets[d] = (int) Math.pow(granularity, size - d - 1);
		int length = 1;
		do {
			// Expand start front, check if it meets goal front.
			for (Point p : startFront) {
				for (int d = 0; d < size; d++) {
					for (int offset = -1; offset <= 1; offset += 2) {
						int newCoord = p.indices[d] + offset;
						if (newCoord >= 0 && newCoord < granularity) {
							int neighbourIndex = p.index + offset * indexOffsets[d];
							if (!obstacleOccludes.get(neighbourIndex)) {
								if (goalCovered.get(neighbourIndex)) {
									return length * stepSize;
								}
								if (!startCovered.get(neighbourIndex)) {
									startCovered.set(neighbourIndex);
									Point neighbour = new Point(ArrayUtils.clone(p.indices), neighbourIndex);
									neighbour.indices[d] = newCoord;
									startFrontNext.add(neighbour);
								}
							}
						}
					}
				}
			}

			length++;

			// Expand goal front, check if it meets start front.
			for (Point p : goalFront) {
				for (int d = 0; d < size; d++) {
					for (int offset = -1; offset <= 1; offset += 2) {
						int newCoord = p.indices[d] + offset;
						if (newCoord >= 0 && newCoord < granularity) {
							int neighbourIndex = p.index + offset * indexOffsets[d];
							if (!obstacleOccludes.get(neighbourIndex)) {
								if (startCovered.get(neighbourIndex)) {
									return length * stepSize;
								}
								if (!goalCovered.get(neighbourIndex)) {
									goalCovered.set(neighbourIndex);
									Point neighbour = new Point(ArrayUtils.clone(p.indices), neighbourIndex);
									neighbour.indices[d] = newCoord;
									goalFrontNext.add(neighbour);
								}
							}
						}
					}
				}
			}

			length++;

			Vector<Point> t = startFront;
			startFront = startFrontNext;
			startFrontNext = t;
			startFrontNext.clear();
			t = goalFront;
			goalFront = goalFrontNext;
			goalFrontNext = t;
			goalFrontNext.clear();

			// If one of the fronts could not be expanded (and they have not yet met), then it means
			// that they are entirely surrounded by obstacles and/or the edge of the environment space.
		} while (!startFront.isEmpty() && !goalFront.isEmpty());

		return -1;
	}

	@Override
	public boolean increaseDifficultyPossible() {
		return getNewObstacleCount() > obstacleCount;
	}

	@Override
	public void increaseDifficulty() {
		obstacleCount = getNewObstacleCount();
	}

	private int getNewObstacleCount() {
		int obstacleCountMax = props.getIntProperty(OBSTACLE_COUNT_MAX);
		if (obstacleCount == obstacleCountMax) {
			return obstacleCount;
		}

		int newObstacleCount = obstacleCount;
		String deltaString = props.getProperty(OBSTACLE_COUNT_INCREASE_DELTA).trim().toLowerCase();
		boolean isFactor = deltaString.endsWith("x");
		double delta = Double.parseDouble(deltaString.replaceAll("x", ""));
		if (delta >= 1) {
			if (!isFactor) {
				newObstacleCount = obstacleCount + (int) Math.round(delta);
			} else if (delta > 1) {
				newObstacleCount = (int) Math.round(obstacleCount * delta);
			}
			if (newObstacleCount > obstacleCountMax) {
				newObstacleCount = obstacleCountMax;
			}
		}
		return newObstacleCount;
	}

	@Override
	public int getMinimumStepsToSolve() {
		return requiredSteps;
	}

	@Override
	public void setMinimumStepsToSolve(int steps) {
		requiredSteps = steps;
	}

	/**
	 * {@inheritDoc} This implementation renders the obstacles in the environment iff the environment size (dimensions)
	 * == 2.
	 */
	@Override
	public void logToImage(String baseFileName, int imageSize) {
		if (size == 2 && obstacleCount > 0) {
			BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_3BYTE_BGR);
			Graphics2D g = image.createGraphics();
			logToImageForTrial(g, imageSize);
			File outputfile = new File(baseFileName + ".png");
			try {
				ImageIO.write(image, "png", outputfile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * {@inheritDoc} This implementation renders the obstacles in the environment image.
	 */
	@Override
	public void logToImageForTrial(Graphics2D g, int imageSize) {
		imageSize -= 1;
		// Draw obstacles.
		g.setColor(Color.GRAY);
		for (int o = 0; o < obstacleCount; o++) {
			if (obstacle[o] != null) {
				int x = (int) Math.round(obstacle[o].corner1.getEntry(0) * imageSize);
				int y = (int) Math.round(obstacle[o].corner1.getEntry(1) * imageSize);
				ArrayRealVector dims = obstacle[o].getSize();
				int width = (int) Math.round(dims.getEntry(0) * imageSize);
				int height = (int) Math.round(dims.getEntry(1) * imageSize);
				g.fillRect(x, y, width, height);
			}
		}
	}

	private static class Point {
		public int[] indices;
		int index;

		public Point(int[] indices, int index) {
			this.indices = indices;
			this.index = index;
		}

		public String toString() {
			return "Point " + index + " (" + Arrays.toString(indices) + ")";
		}
	}

	@Override
	public int getOutputSize() {
		return size + 1;
	}

	@Override
	public int getInputSize() {
		return size;
	}

	@Override
	public String toString() {
		String out = "\t\t" + super.toString();
		out += "\n\t\tObstacle locations and (radius):\n";
		for (int o = 0; o < obstacleCount; o++) {
			if (obstacle[o] != null) {
				out += "\t\t\t" + obstacle[o];
			}
		}
		return out;
	}
	
	protected class Obstacle {
		public ArrayRealVector corner1, corner2;
		public Obstacle(double obstSize) {
			ArrayRealVector dims = new ArrayRealVector(size, obstSize);
			dims.setEntry(random.nextInt(size), maxStepSize*1.01); // Make the obstacle a line/plane.
			corner1 = new ArrayRealVector(size);
			for (int d = 0; d < size; d++) {
				double start = -obstSize/2, end = 1-obstSize/2, range = end-start;
				corner1.setEntry(d, random.nextDouble() * range + start);
			}
			corner2 = corner1.add(dims);
		}
		
		public boolean collision(ArrayRealVector p) {
			for (int d = 0; d < size; d++) {
				if (p.getEntry(d) < corner1.getEntry(d) || p.getEntry(d) > corner2.getEntry(d))
					return false;
			}
			return true;
		}
		
		public ArrayRealVector getSize() {
			return corner2.subtract(corner1);
		}
		
		@Override
		public String toString() {
			return corner1 + " -> " + corner2 + " (" + getSize() + ")";
		}
	}
}