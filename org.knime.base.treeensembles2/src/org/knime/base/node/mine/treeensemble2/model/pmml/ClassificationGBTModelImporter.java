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
 *   10.10.2017 (Adrian Nembach): created
 */
package org.knime.base.node.mine.treeensemble2.model.pmml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.MININGFUNCTION;
import org.dmg.pmml.MULTIPLEMODELMETHOD;
import org.dmg.pmml.MiningModelDocument.MiningModel;
import org.dmg.pmml.OutputFieldDocument.OutputField;
import org.dmg.pmml.RegressionModelDocument.RegressionModel;
import org.dmg.pmml.RegressionTableDocument.RegressionTable;
import org.dmg.pmml.SegmentDocument.Segment;
import org.dmg.pmml.SegmentationDocument.Segmentation;
import org.dmg.pmml.TargetDocument.Target;
import org.knime.base.node.mine.treeensemble2.data.TreeTargetNumericColumnMetaData;
import org.knime.base.node.mine.treeensemble2.learner.TreeNodeSignatureFactory;
import org.knime.base.node.mine.treeensemble2.model.AbstractTreeEnsembleModel.TreeType;
import org.knime.base.node.mine.treeensemble2.model.MultiClassGradientBoostedTreesModel;
import org.knime.base.node.mine.treeensemble2.model.TreeModelRegression;
import org.knime.base.node.mine.treeensemble2.model.TreeNodeRegression;
import org.knime.base.node.mine.treeensemble2.model.TreeNodeSignature;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.Pair;

/**
 * Handles the import of {@link MultiClassGradientBoostedTreesModel}s from PMML.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class ClassificationGBTModelImporter extends AbstractGBTModelImporter<MultiClassGradientBoostedTreesModel> {

    /**
     *
     */
    private static final String NO_REGRESSION_SEGMENT_MESSAGE = "Invalid GBT PMML. The model must contain a final "
        + "segment that performs the softmax transformation on the class logits";

    /**
     * @param conditionParser
     * @param signatureFactory
     * @param treeFactory
     * @param metaDataMapper
     */
    public ClassificationGBTModelImporter(final ConditionParser conditionParser,
        final TreeNodeSignatureFactory signatureFactory,
        final TreeFactory<TreeNodeRegression, TreeModelRegression> treeFactory,
        final MetaDataMapper<TreeTargetNumericColumnMetaData> metaDataMapper) {
        super(conditionParser, signatureFactory, treeFactory, metaDataMapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected MultiClassGradientBoostedTreesModel importFromPMMLInternal(final MiningModel miningModel) {
        final Segmentation modelChain = miningModel.getSegmentation();
        CheckUtils.checkArgument(modelChain.getMultipleModelMethod() == MULTIPLEMODELMETHOD.MODEL_CHAIN,
            "The top level segmentation should have multiple model method '%s' but has '%s'",
            MULTIPLEMODELMETHOD.MODEL_CHAIN, modelChain.getMultipleModelMethod());
        final List<List<TreeModelRegression>> trees = new ArrayList<>();
        final List<List<Map<TreeNodeSignature, Double>>> coefficientMaps = new ArrayList<>();
        final List<String> classLabels = new ArrayList<>();
        final List<Segment> segments = modelChain.getSegmentList();
        CheckUtils.checkArgument(segments.size() > 2, "Invalid GBT PMML. "
            + "The model must contain at least two class segments and a final regression segment.");
        for (int i = 0; i < segments.size() - 1; i++) {
            final Pair<List<TreeModelRegression>, List<Map<TreeNodeSignature, Double>>> gbtPair =
                processClassSegment(segments.get(i));
            trees.add(gbtPair.getFirst());
            coefficientMaps.add(gbtPair.getSecond());
            classLabels.add(extractClassLabel(segments.get(i)));
        }
        final double initialValue = extractInitialValue(segments.get(0), segments.get(segments.size() - 1));
        return MultiClassGradientBoostedTreesModel.create(getMetaDataMapper().getTreeMetaData(), trees, coefficientMaps,
            initialValue, TreeType.Ordinary, classLabels);
    }

    private static double extractInitialValue(final Segment classSegment, final Segment regressionSegment) {
        CheckUtils.checkArgumentNotNull(classSegment, "The class segment was null. This indicates a coding error.");
        final MiningModel miningModel = classSegment.getMiningModel();
        CheckUtils.checkArgumentNotNull(miningModel,
            "The mining model of a class segment is null. This indicates a coding error.");
        if (classSegment.getMiningModel().getTargets() != null) {
            return extractInitialValueFromClassSegment(classSegment);
        } else {
            return extractInitialValueFromRegressionSegment(regressionSegment);
        }
    }

    private static double extractInitialValueFromClassSegment(final Segment classSegment) {
        final List<Target> ts = classSegment.getMiningModel().getTargets().getTargetList();
        CheckUtils.checkArgument(ts.size() == 1,
            "There must be exactly one target field in each class segment but there were %d", ts.size());
        return ts.get(0).getRescaleConstant();
    }

    private static double extractInitialValueFromRegressionSegment(final Segment regressionSegment) {
        CheckUtils.checkArgumentNotNull(regressionSegment, NO_REGRESSION_SEGMENT_MESSAGE);
        final RegressionModel regressionModel = regressionSegment.getRegressionModel();
        CheckUtils.checkArgumentNotNull(regressionModel, NO_REGRESSION_SEGMENT_MESSAGE);
        final List<RegressionTable> regressionTables = regressionModel.getRegressionTableList();
        CheckUtils.checkArgument(regressionTables.size() > 1, "There must be at least two classes present in the final regression table.");
        final double initialValue = regressionTables.get(0).getIntercept();
        for (RegressionTable regressionTable : regressionTables) {
            if (regressionTable.getIntercept() != initialValue) {
                throw new IllegalArgumentException("Varying initial values for Gradient Boosted Trees detected. "
                    + "This is currently not supported.");
            }
        }
        return initialValue;
    }

    private String extractClassLabel(final Segment classSegment) {
        List<OutputField> ofs = classSegment.getMiningModel().getOutput().getOutputFieldList();
        CheckUtils.checkArgument(ofs.size() == 1,
            "There must be exactly one output field in each class segment but there were %d.", ofs.size());
        String wrappedName = ofs.get(0).getName();
        String name = wrappedName.replaceFirst("gbtValue\\(", "");
        return name.substring(0, name.length() - 1);
    }

    private Pair<List<TreeModelRegression>, List<Map<TreeNodeSignature, Double>>>
        processClassSegment(final Segment segment) {
        CheckUtils.checkArgumentNotNull(segment, "A class segment was null. This indicates a coding error.");
        MiningModel m = segment.getMiningModel();
        CheckUtils.checkArgumentNotNull(m, "Invalid GBT PMML. The class segment %s does not contain a mining model.",
            segment);
        CheckUtils.checkArgument(m.getFunctionName() == MININGFUNCTION.REGRESSION,
            "The mining function of a class segment mining model must be '%s' but was '%s'.", MININGFUNCTION.REGRESSION,
            m.getFunctionName());
        final Segmentation sumSegmentation = m.getSegmentation();
        CheckUtils.checkArgumentNotNull(sumSegmentation,
            "Invalid GBT PMML. The class segment mining model %s does not contain a segmentation.");
        return readSumSegmentation(sumSegmentation);
    }

}
