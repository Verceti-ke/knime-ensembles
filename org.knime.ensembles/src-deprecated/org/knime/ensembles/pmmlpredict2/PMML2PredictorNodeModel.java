/*
 * ------------------------------------------------------------------------
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
 * -------------------------------------------------------------------
 *
 */
package org.knime.ensembles.pmmlpredict2;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.knime.base.node.mine.bayes.naivebayes.predictor3.NaiveBayesPredictorNodeModel2;
import org.knime.base.node.mine.cluster.assign.ClusterAssignerNodeModel;
import org.knime.base.node.mine.decisiontree2.predictor2.DecTreePredictorNodeModel;
import org.knime.base.node.mine.neural.mlp2.MLPPredictorNodeModel;
import org.knime.base.node.mine.regression.predict2.RegressionPredictorNodeModel;
import org.knime.base.node.mine.svm.predictor2.SVMPredictorNodeModel;
import org.knime.base.node.mine.treeensemble2.node.gradientboosting.predictor.pmml.GradientBoostingPMMLPredictorNodeModel;
import org.knime.base.node.mine.treeensemble2.node.regressiontree.predictor.RegressionTreePMMLPredictorNodeModel;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.pmml.PMMLPortObject;
import org.knime.core.pmml.PMMLModelType;
import org.knime.ensembles.pmml.predictor2.PMMLEnsemblePredictor2NodeModel;
import org.w3c.dom.Node;

/**
 * The node model for the general pmml predictor.
 *
 * @author Iris Adae, University of Konstanz, Germany
 * @deprecated
 */
@Deprecated
public class PMML2PredictorNodeModel extends NodeModel {

    private static final int PMML_PORT = 0;

    /**
     * Creates a new model with a PMML input and a data output.
     */
    protected PMML2PredictorNodeModel() {
        super(new PortType[] {
                PMMLPortObject.TYPE,
                BufferedDataTable.TYPE},
                new PortType[] {BufferedDataTable.TYPE});
    }

    /** {@inheritDoc} */
    @Override
    protected PortObject[] execute(final PortObject[] inObjects,
        final ExecutionContext exec)
                throws Exception {
        PMMLPortObject port = (PMMLPortObject) inObjects[PMML_PORT];

        Set<PMMLModelType> types =  port.getPMMLValue().getModelTypes();
        if (types.size() < 1) {
            throw new InvalidSettingsException("No PMML Model found.");
        }

        PMMLModelType type = types.iterator().next();

        if (types.size() > 1) {
            setWarningMessage(
                "More models are found, the first one is used "
                        + " : " + type.toString());
        }

        List<Node> models = port.getPMMLValue().getModels(type);
        if (models.isEmpty()) {
            throw new InvalidSettingsException("No PMML Model found.");
        }

        switch (type) {
            case ClusteringModel: {
                ClusterAssignerNodeModel model = new ClusterAssignerNodeModel();
                return model.execute(inObjects, exec);
            }
            case GeneralRegressionModel:
            case RegressionModel: {
                RegressionPredictorNodeModel model = new RegressionPredictorNodeModel();
                return model.execute(inObjects, exec);
            }
            case TreeModel: {
                if (isSimpleRegressionTree(models.get(0))) {
                    RegressionTreePMMLPredictorNodeModel model = new RegressionTreePMMLPredictorNodeModel();
                    return model.execute(inObjects, exec);
                }
                DecTreePredictorNodeModel model = new DecTreePredictorNodeModel();
                return model.execute(inObjects, exec);
            }
            case SupportVectorMachineModel: {
                SVMPredictorNodeModel model = new SVMPredictorNodeModel();
                return model.execute(inObjects, exec);
            }
            case NeuralNetwork: {
                MLPPredictorNodeModel model = new MLPPredictorNodeModel();
                return model.execute(inObjects, exec);
            }
            case MiningModel: {
                if (isGradientBoostedTreesModel(models.get(0))) {
                    GradientBoostingPMMLPredictorNodeModel<?> model =
                            getGradientBoostedTreesPredictorNodeModel(models.get(0));
                    return model.execute(inObjects, exec);
                }
                PMMLEnsemblePredictor2NodeModel model = new PMMLEnsemblePredictor2NodeModel();
                return model.execute(inObjects, exec);
            }
            case NaiveBayesModel: {
                NaiveBayesPredictorNodeModel2 model = new NaiveBayesPredictorNodeModel2();
                return model.execute(inObjects, exec);
            }
            default:
                // this should never happen.
                throw new InvalidSettingsException("No suitable predictor found for these model types.");
        }
    }

    private GradientBoostingPMMLPredictorNodeModel<?> getGradientBoostedTreesPredictorNodeModel(final Node miningModel) {
        String functionName = miningModel.getAttributes().getNamedItem("functionName").getNodeValue();
        return new GradientBoostingPMMLPredictorNodeModel(functionName.equals("regression"));
    }

    private boolean isSimpleRegressionTree(final Node treeModel) {
        if (treeModel.getNodeName() != "TreeModel") {
            return false;
        }
        return treeModel.getAttributes().getNamedItem("functionName").getNodeValue().equals("regression");
    }

    private boolean isGradientBoostedTreesModel(final Node miningModel) {
        Node modelName = miningModel.getAttributes().getNamedItem("modelName");
        return modelName == null ? false : modelName.getNodeValue().equals("GradientBoostedTrees");
    }

    /** {@inheritDoc} */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
            throws InvalidSettingsException {
        return new PortObjectSpec[]{null};
    }

    /** {@inheritDoc} */
    @Override
    protected void loadInternals(final File nodeInternDir,
            final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        // nothing to load

    }

    /** {@inheritDoc} */
    @Override
    protected void saveInternals(
            final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        // no op
    }

    /** {@inheritDoc} */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
    }

    /** {@inheritDoc} */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    }

    /** {@inheritDoc} */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    }

    /** {@inheritDoc} */
    @Override
    protected void reset() {
        // no op
    }

}
