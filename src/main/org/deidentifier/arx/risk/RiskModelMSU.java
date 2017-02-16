/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2016 Fabian Prasser, Florian Kohlmayer and contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deidentifier.arx.risk;

import java.util.HashSet;
import java.util.Set;

import org.deidentifier.arx.DataHandleInternal;
import org.deidentifier.arx.common.WrappedBoolean;
import org.deidentifier.arx.common.WrappedInteger;
import org.deidentifier.arx.exceptions.ComputationInterruptedException;
import org.deidentifier.arx.risk.msu.SUDA2;
import org.deidentifier.arx.risk.msu.SUDA2ProgressListener;
import org.deidentifier.arx.risk.msu.SUDA2Result;

/**
 * A risk model based on MSUs in the data set
 * @author Fabian Prasser
 *
 */
public class RiskModelMSU {

    /** Progress stuff */
    private final WrappedInteger progress;
    /** Progress stuff */
    private final WrappedBoolean stop;
    /** Maximal size of an MSU considered */
    private final int            maxK;
    /** The number of MSUs */
    private int                  numMSUs = 0;
    /** Contributions of each column */
    private final double[]       columnKeyContributions;
    /** Contributions of each column */
    private final int[]          columnKeyMinSize;
    /** Contributions of each column */
    private final int[]          columnKeyMaxSize;
    /** Contributions of each column */
    private final double[]       columnKeyAverageSize;
    /** Distribution of sizes of MSUs */
    private final double[]       sizeDistribution;

    /**
     * Creates a new instance
     * @param handle
     * @param identifiers
     * @param stop 
     * @param progress 
     * @param maxK
     */
    RiskModelMSU(DataHandleInternal handle, 
                 Set<String> identifiers, 
                 WrappedInteger progress, 
                 WrappedBoolean stop,
                 int maxK) {

        // Store
        this.stop = stop;
        this.progress = progress;
        
        // Add all attributes, if none were specified
        if (identifiers == null || identifiers.isEmpty()) {
            identifiers = new HashSet<String>();
            for (int column = 0; column < handle.getNumColumns(); column++) {
                identifiers.add(handle.getAttributeName(column));
            }
        }
        
        // Build column array
        int[] columns = getColumns(handle, identifiers);
        
        // Update progress
        progress.value = 10;
        checkInterrupt();
        
        // Do something
        SUDA2 suda2 = new SUDA2(handle.getDataMatrix(columns).getMatrix());
        suda2.setProgressListener(new SUDA2ProgressListener() {
            @Override
            public void update(double progress) {
                RiskModelMSU.this.progress.value = 10 + (int)(progress * 90d);
            }
        });
        suda2.setStopFlag(stop);
        SUDA2Result result = suda2.suda2(maxK);
        this.maxK = result.getMaxK();
        this.numMSUs = result.getNumMSUs();
        
        int[] _columnKeyContributions = result.getColumnKeyContributions();
        int[] _sizeDistribution = result.getKeySizeDistribution();
        int[] _columnKeyAverageSize = result.getColumnKeyAverageSize();

        // Key contributions
        this.columnKeyContributions = new double[_columnKeyContributions.length];
        for (int i=0; i < this.columnKeyContributions.length; i++) {
            this.columnKeyContributions[i] = (double)_columnKeyContributions[i] / (double)this.numMSUs;
        }
        
        // Average size
        this.columnKeyAverageSize = new double[_columnKeyAverageSize.length];
        for (int i=0; i < this.columnKeyAverageSize.length; i++) {
            this.columnKeyAverageSize[i] = (double)_columnKeyAverageSize[i] / (double)_columnKeyContributions[i];
        }

        // Size distribution
        this.sizeDistribution = new double[_sizeDistribution.length];
        for (int i=0; i < this.sizeDistribution.length; i++) {
            this.sizeDistribution[i] = (double)_sizeDistribution[i] / (double)this.numMSUs;
        }
        
        // Others
        this.columnKeyMinSize = result.getColumnKeyMinSize();
        this.columnKeyMaxSize = result.getColumnKeyMaxSize();
    }
    
    /**
     * @return the numMSUs
     */
    public int getNumMSUs() {
        return numMSUs;
    }

    /**
     * @param numMSUs the numMSUs to set
     */
    public void setNumMSUs(int numMSUs) {
        this.numMSUs = numMSUs;
    }

    /**
     * @return the maxK
     */
    public int getMaxK() {
        return maxK;
    }

    /**
     * @return the columnKeyContributions
     */
    public double[] getColumnKeyContributions() {
        return columnKeyContributions;
    }

    /**
     * @return the columnKeyMinSize
     */
    public int[] getColumnKeyMinSize() {
        return columnKeyMinSize;
    }

    /**
     * @return the columnKeyMaxSize
     */
    public int[] getColumnKeyMaxSize() {
        return columnKeyMaxSize;
    }

    /**
     * @return the columnKeyAverageSize
     */
    public double[] getColumnKeyAverageSize() {
        return columnKeyAverageSize;
    }

    /**
     * @return the sizeDistribution
     */
    public double[] getMSUSizeDistribution() {
        return sizeDistribution;
    }

    /**
     * Checks for interrupts
     */
    private void checkInterrupt() {
        if (stop.value) { throw new ComputationInterruptedException(); }
    }

    /**
     * Returns the column array
     * @param handle
     * @param identifiers
     * @return
     */
    private int[] getColumns(DataHandleInternal handle, Set<String> identifiers) {
        int[] result = new int[identifiers.size()];
        int index = 0;
        for (String attribute : identifiers) {
            int column = handle.getColumnIndexOf(attribute);
            if (column == -1) {
                throw new IllegalArgumentException("Unknown attribute '" + attribute+"'");
            }
            result[index++] = column;
        }
        return result;
    }
}