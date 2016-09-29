/*
 * The MIT License
 *
 * Copyright 2016 angel.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package bcep;

import epm_algorithms.Model;
import java.util.HashMap;
import keel.Dataset.InstanceSet;
import keel.Dataset.Attribute;
import Utils.*;
import epm_algorithms.Main;
import exceptions.IllegalActionException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.util.Pair;
import keel.Dataset.Attributes;
import static sjep_classifier.SJEP_Classifier.getInstances;
import static sjep_classifier.SJEP_Classifier.getSimpleItems;
import static sjep_classifier.SJEP_Classifier.median;
import static sjep_classifier.SJEP_Classifier.minSupp;

/**
 *
 * @author Ángel M. García-Vico
 * @version 1.0
 * @since JDK 1.8
 */
public class BCEP_Model extends Model {

    ArrayList<Pattern> patterns;
    ArrayList<ArrayList<Pattern>> allPatterns;

    public BCEP_Model() {
        super.setFullyQualifiedName("bcep.BCEP_Model");
    }

    @Override
    public void learn(InstanceSet training, HashMap<String, String> params) {

        try {
            // First, check if the dataset has the correct format.
            checkDataset();

            int countD1 = 0;
            int countD2 = 0;
            float minSupp = Float.parseFloat(params.get("Minimum support"));
            ArrayList<String> classes = new ArrayList<>(Attributes.getOutputAttribute(0).getNominalValuesList());
            // Gets the count of examples for each class to calculate the growth rate.
            for (int i = 0; i < training.getNumInstances(); i++) {
                if (classes.indexOf(training.getInstance(i).getOutputNominalValues(0)) == 0) {
                    countD1++;
                } else {
                    countD2++;
                }
            }

            if (Attributes.getOutputAttribute(0).getNumNominalValues() <= 2) {
                // get simple itemsets to perform the ordering of the items and filter by gorwth rate
                // Class '0' is considered as positive
                ArrayList<Item> simpleItems = getSimpleItems(training, minSupp, 0);
                // sort items by growth rate
                simpleItems.sort(null);
                // gets all instances removing those itemset that not appear on simpleItems
                ArrayList<Pair<ArrayList<Item>, Integer>> instances = getInstances(training, simpleItems, 0);
                for (int i = 0; i < instances.size(); i++) {
                    // sort each arraylist of items
                    instances.get(i).getKey().sort(null);
                }

                System.out.println("Loading the CP-Tree...");
                // Create the CP-Tree
                CPTree tree = new CPTree(countD1, countD2);
                // Add the instances on the CP-Tree
                for (Pair<ArrayList<Item>, Integer> inst : instances) {
                    tree.insertTree(inst.getKey(), inst.getValue());
                }

                System.out.println("Mining SJEPs...");
                // Perform mining
                patterns = tree.mineTree(minSupp);
                ArrayList<Pair<ArrayList<Item>, Integer>> inst = getInstances(training, simpleItems, 0);
                patterns = pruneEPs(patterns, inst);
                Main.setInfoLearnText("Mining finished. eJEPs found: " + patterns.size());
            } else {
                // MULTICLASS EXECUTION
                // Execute the mining algorithm k times, with k the number of classes.
                allPatterns = new ArrayList<>();
                for (int i = 0; i < Attributes.getOutputAttribute(0).getNumNominalValues(); i++) {
                    // count the number of examples in the new binarized dataset
                    countD1 = countD2 = 0;
                    for (int j = 0; j < training.getNumInstances(); j++) {
                        if (training.getInstance(i).getOutputNominalValuesInt(0) == i) {
                            countD1++;
                        } else {
                            countD2++;
                        }
                    }

                    System.out.println("Mining class: " + Attributes.getOutputAttribute(0).getNominalValue(i));
                    // Class 'i' is considered de positive class, the rest of classes correspond to the negative one.
                    // Get the simple items.
                    ArrayList<Item> simpleItems = getSimpleItems(training, minSupp, i);
                    // sort items by growth rate
                    simpleItems.sort(null);
                    // gets all instances removing those itemset that not appear on simpleItems
                    ArrayList<Pair<ArrayList<Item>, Integer>> instances = getInstances(training, simpleItems, i);
                    for (int j = 0; j < instances.size(); j++) {
                        // sort each arraylist of items
                        instances.get(j).getKey().sort(null);
                    }

                    System.out.println("Loading the CP-Tree...");
                    // Create the CP-Tree
                    CPTree tree = new CPTree(countD1, countD2);
                    // Add the instances on the CP-Tree
                    for (Pair<ArrayList<Item>, Integer> inst : instances) {
                        tree.insertTree(inst.getKey(), inst.getValue());
                    }

                    System.out.println("Mining SJEPs...");
                    // Perform mining
                    ArrayList<Pattern> patterns = tree.mineTree(minSupp);
                    // remove those patterns with class != 0 and change the value of the class
                    Iterator it = patterns.iterator();
                    while (it.hasNext()) {
                        Pattern next = (Pattern) it.next();
                        if (next.getClase() != 0) {
                            it.remove();
                        } else {
                            next.setClase(i);
                        }
                    }
                    if (patterns.isEmpty()) {
                        allPatterns.add(new ArrayList<>());
                    } else {
                        ArrayList<Pair<ArrayList<Item>, Integer>> inst = getInstances(training, simpleItems, i);
                        patterns = pruneEPs(patterns, inst);
                        allPatterns.add(patterns);
                    }
                }

                int sum = 0;
                for (ArrayList<Pattern> patterns : allPatterns) {
                    sum += patterns.size();
                    for (Pattern pat : patterns) {
                        patterns.add(pat);
                    }
                }
                Main.setInfoLearnText("Mining finished. eJEPs found: " + sum);
            }
        } catch (IllegalActionException ex) {
            Main.setInfoLearnTextError(ex.getReason());
        }

    }

    @Override
    public String[] predict(InstanceSet test) {
        System.out.println("Que NO estoy hecho aun!");

    }

    @Override
    public HashMap<String, Double> test(InstanceSet test) {
        int[][] confusionMatrices = new int[patterns.size()][5];
        ArrayList<HashMap<String, Double>> qm;

        ArrayList<Item> simpleItems = getSimpleItems(test, minSupp, 0);
        ArrayList<Pair<ArrayList<Item>, Integer>> testInstances = getInstances(test, simpleItems, 0);

        // Calculate the confusion matrix of each pattern to calculate the quality measures.
        int sumNvars = 0;
        for (int i = 0; i < patterns.size(); i++) {
            int tp = 0;
            int tn = 0;
            int fp = 0;
            int fn = 0;
            // for each instance
            for (int j = 0; j < testInstances.size(); j++) {
                // If the pattern covers the example
                if (patterns.get(i).covers(testInstances.get(j).getKey())) {

                    if (patterns.get(i).getClase() == testInstances.get(j).getValue()) {
                        tp++;
                    } else {
                        fp++;
                    }
                } else if (patterns.get(i).getClase() != testInstances.get(j).getValue()) {
                    tn++;
                } else {
                    fn++;
                }

            }

            confusionMatrices[i][0] = tp;
            confusionMatrices[i][1] = tn;
            confusionMatrices[i][2] = fp;
            confusionMatrices[i][3] = fn;
            confusionMatrices[i][4] = patterns.get(i).getItems().size();
        }

        // CALCULATE HERE DESCRIPTIVE QUALITY MEASURES FOR EACH PATTERN
        qm = Model.calculateMeasuresFromConfusionMatrix(confusionMatrices);

        // FILTER HERE THE RULES: GETS THE BEST n RULES AND THE BEST n RULES FOR EACH CLASS.
        // CALCULATE HERE THE ACCURACY AND AUC OF THE MODEL (FILTERED BY CLASS OR GLOBAL AND NON FILTERED)
        HashMap<String, Double> AverageQualityMeasures = Model.AverageQualityMeasures(qm);
        
        // make the average of the quality measures

        return AverageQualityMeasures;
    }

    @Override
    public String toString() {
        return "UNSUPPORTED";
    }

    /**
     * Checks if the dataset is processable by the method, i.e. it checks if all
     * its attributes are nominal.
     *
     * @throws exceptions.IllegalActionException
     */
    public void checkDataset() throws exceptions.IllegalActionException {
        for (int i = 0; i < Attributes.getInputNumAttributes(); i++) {
            if (Attributes.getAttribute(i).getType() != Attribute.NOMINAL) {
                throw new exceptions.IllegalActionException("ERROR: The dataset must contain only nominal attributes. Please, discretize the real ones.");
            }
        }

    }

    /**
     * It prunes the set of essential JEPs mined following by data class
     * coverage procedure.
     *
     * @param patterns
     * @param training
     * @return
     */
    private ArrayList<Pattern> pruneEPs(ArrayList<Pattern> patterns, ArrayList<Pair<ArrayList<Item>, Integer>> training) {
        // Sort the patterns by ranking.
        patterns.sort(new Comparator<Pattern>() {
            @Override
            public int compare(Pattern o1, Pattern o2) {
                if (o1.getSupport() > o2.getSupport()) {
                    return 1;
                } else if (o1.getSupport() < o2.getSupport()) {
                    return -1;
                } else if (o1.getItems().size() > o2.getItems().size()) {
                    return 1;
                } else if (o1.getItems().size() < o2.getItems().size()) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });

        // Apply the data class covering procedure
        boolean allCovered = true;
        int counter = 0;
        ArrayList<Pattern> result = new ArrayList<>();
        boolean[] tokens = new boolean[training.size()];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = false;
        }

        do {
            boolean coverNew = false;
            allCovered = true;
            for (int i = 0; i < training.size(); i++) {
                if (patterns.get(counter).covers(training.get(i).getKey()) && !tokens[i]) {
                    coverNew = true;
                    tokens[i] = true;
                }
            }

            if (coverNew) {
                result.add(patterns.get(counter));
            }

            for (int i = 0; i < tokens.length && allCovered; i++) {
                if (!tokens[i]) {
                    allCovered = false;
                }
            }

            counter++;
        } while (!allCovered && counter < patterns.size());

        return result;

    }

    public static void calculateAccuracy(InstanceSet testSet, int[] predictions) {
        // we consider class 0 as positive and class 1 as negative
        int tp = 0;
        int fp = 0;
        int tn = 0;
        int fn = 0;

        // Calculate the confusion matrix.
        for (int i = 0; i < predictions.length; i++) {
            if (testSet.getOutputNumericValue(i, 0) == 0) {
                if (predictions[i] == 0) {
                    tp++;
                } else {
                    fn++;
                }
            } else if (predictions[i] == 0) {
                fp++;
            } else {
                tn++;
            }
        }

        System.out.println("Test Accuracy: " + ((double) (tp + tn) / (double) (tp + tn + fp + fn)) * 100 + "%");
    }

    public static void computeAccuracy(ArrayList<Pair<ArrayList<Item>, Integer>> testInstances, ArrayList<Pattern> patterns, InstanceSet test, int countD1, int countD2) {
        int[] predictions = new int[testInstances.size()];
        //Now, for each pattern
        for (int i = 0; i < testInstances.size(); i++) {
            // calculate the score for each class for classify:
            double scoreD1 = 0;
            double scoreD2 = 0;
            ArrayList<Integer> scoresD1 = new ArrayList<>();  // This is to calculate the base-score, that is the median
            ArrayList<Integer> scoresD2 = new ArrayList<>();
            // for each pattern mined
            for (int j = 0; j < patterns.size(); j++) {
                if (patterns.get(j).covers(testInstances.get(i).getKey())) {
                    // If the example is covered by the pattern.
                    // sum it support to the class of the pattern
                    if (testInstances.get(i).getValue() == 0) {
                        scoreD1 += patterns.get(j).getSupport();
                        scoresD1.add(patterns.get(j).getSupport());
                    } else {
                        scoreD2 += patterns.get(j).getSupport();
                        scoresD2.add(patterns.get(j).getSupport());
                    }

                }
            }

            // Now calculate the normalized score to make the prediction
            double medianD1 = median(scoresD1);
            double medianD2 = median(scoresD2);

            if (medianD1 == 0) {
                scoreD1 = 0;
            } else {
                scoreD1 = scoreD1 / medianD1;
            }

            if (medianD2 == 0) {
                scoreD2 = 0;
            } else {
                scoreD2 = scoreD2 / medianD2;
            }

            // make the prediction:
            if (scoreD1 > scoreD2) {
                predictions[i] = 0;
            } else if (scoreD1 < scoreD2) {
                predictions[i] = 1;
            } else // In case of ties, the majority class is setted
            {
                if (countD1 < countD2) {
                    predictions[i] = 0;
                } else {
                    predictions[i] = 1;
                }
            }
        }

        calculateAccuracy(test, predictions);
    }

}
