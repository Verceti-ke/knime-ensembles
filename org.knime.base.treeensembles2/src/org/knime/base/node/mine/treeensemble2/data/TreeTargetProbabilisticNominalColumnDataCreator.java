/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Aug 30, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.mine.treeensemble2.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.probability.ProbabilityDistributionValue;
import org.knime.core.node.util.CheckUtils;

/**
 * Creates TreeTargetProbabilisticNominalColumnData from {@link ProbabilityDistributionValue
 * ProbabilityDistributionValues}.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class TreeTargetProbabilisticNominalColumnDataCreator extends TreeTargetColumnDataCreator {

    private final List<double[]> m_data = new ArrayList<>();

    private final NominalValueRepresentation[] m_nomValReps;

    /**
     * @param colSpec
     */
    @SuppressWarnings("null") // it is explicitly checked that elementNames is not null
    TreeTargetProbabilisticNominalColumnDataCreator(final DataColumnSpec colSpec) {
        super(colSpec);
        CheckUtils.checkArgument(colSpec.getType().isCompatible(ProbabilityDistributionValue.class),
            "Type is not probabilistic: %s", colSpec.getName());
        final List<String> elementNames = colSpec.getElementNames();
        CheckUtils.checkArgument(elementNames != null && !elementNames.isEmpty(),
            "A probability distribution column must always specify its element names.");
        m_nomValReps = IntStream.range(0, elementNames.size())
            .mapToObj(i -> new NominalValueRepresentation(elementNames.get(i), i, 0.0))
            .toArray(NominalValueRepresentation[]::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void add(final DataCell c) {
        final ProbabilityDistributionValue p = (ProbabilityDistributionValue)c;
        CheckUtils.checkArgument(p.size() == m_nomValReps.length,
            "Encountered probability distribution value has not the expected number of probabilities. %s vs %s",
            p.size(), m_nomValReps.length);
        final double[] probabilities = new double[m_nomValReps.length];
        double max = Double.NEGATIVE_INFINITY;
        NominalValueRepresentation maxRep = null;
        for (int i = 0; i < p.size(); i++) {
            final double probability = p.getProbability(i);
            final NominalValueRepresentation classRep = m_nomValReps[i];
            probabilities[classRep.getAssignedInteger()] = probability;
            if (probability > max) {
                max = probability;
                maxRep = classRep;
            }
        }
        incrementFrequency(maxRep);
        m_data.add(probabilities);
    }

    private static void incrementFrequency(final NominalValueRepresentation maxRep) {
        assert maxRep != null;
        maxRep.addToFrequency(1.0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    TreeTargetProbabilisticNominalColumnData createColumnData() {
        assert !Arrays.asList(m_nomValReps).contains(null);
        TreeTargetNominalColumnMetaData metaData =
            new TreeTargetNominalColumnMetaData(getColumnSpec().getName(), m_nomValReps);
        return new TreeTargetProbabilisticNominalColumnData(metaData, getRowKeys(),
            m_data.stream().sequential().toArray(double[][]::new));
    }

}
