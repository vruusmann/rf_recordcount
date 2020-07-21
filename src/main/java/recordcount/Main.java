package recordcount;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.adapters.NodeAdapter;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.mining.Segmentation.MultipleModelMethod;
import org.dmg.pmml.tree.ComplexNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.NodeTransformer;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.ModelEvaluator;
import org.jpmml.evaluator.TargetField;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.mining.HasSegmentation;
import org.jpmml.evaluator.mining.SegmentResult;
import org.jpmml.evaluator.tree.HasDecisionPath;
import org.jpmml.model.metro.MetroJAXBUtil;
import org.jpmml.model.visitors.VisitorBattery;
import recordcount.visitors.RecordCountCleaner;

public class Main {

	@Parameter (
		names = "--help",
		description = "Show the list of configuration options and exit",
		help = true
	)
	private boolean help = false;

	@Parameter (
		names = {"--pmml-input"},
		description = "PMML model file",
		required = true
	)
	private File pmmlInputFile = null;

	@Parameter (
		names = {"--pmml-output"},
		description = "PMML output file"
	)
	private File pmmlOutputFile = null;

	@Parameter (
		names = {"--csv-input"},
		description = "CSV input file",
		required = true
	)
	private File csvInputFile = null;


	static
	public void main(String... args) throws Exception {
		Main main = new Main();

		JCommander commander = new JCommander(main);
		commander.setProgramName(Main.class.getName());

		try {
			commander.parse(args);
		} catch(ParameterException pe){
			StringBuilder sb = new StringBuilder();

			sb.append(pe.toString());
			sb.append("\n");

			commander.usage(sb);

			System.err.println(sb.toString());

			System.exit(-1);
		}

		if(main.help){
			StringBuilder sb = new StringBuilder();

			commander.usage(sb);

			System.out.println(sb.toString());

			System.exit(0);
		}

		main.run();
	}

	private void run() throws Exception {
		ModelEvaluator<?> evaluator = loadModelEvaluator(this.pmmlInputFile);

		// Supervised learning models are expected to have exactly one target field (aka label)
		TargetField targetField = Iterables.getOnlyElement(evaluator.getTargetFields());

		Model model = evaluator.getModel();

		if(model instanceof MiningModel){
			MiningModel miningModel = (MiningModel)model;

			Segmentation segmentation = miningModel.getSegmentation();

			MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
			switch(multipleModelMethod){
				case SUM:
				case WEIGHTED_SUM:
				case AVERAGE:
				case WEIGHTED_AVERAGE:
				case MEDIAN:
				case WEIGHTED_MEDIAN:
					break;
				default:
					throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
			}
		} else

		{
			throw new UnsupportedElementException(model);
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.csvInputFile), "UTF-8"));

		Splitter splitter = Splitter.on(Main.CSV_SEPARATOR);

		// The first line of the CSV input file is field names
		String inputHeaderRow = reader.readLine();

		List<FieldName> inputFields = (splitter.splitToList(inputHeaderRow)).stream()
			.map(string -> FieldName.create(string))
			.collect(Collectors.toList());

		for(int row = 0; true; row++){
			String contentRow = reader.readLine();
			if(contentRow == null){
				break;
			}

			List<String> content = splitter.splitToList(contentRow);

			Map<FieldName, Object> arguments = new LinkedHashMap<>();

			for(int column = 0; column < content.size(); column++){
				arguments.put(inputFields.get(column), content.get(column));
			}

			String expectedTargetValue = (String)arguments.remove(targetField.getName());

			Map<FieldName, ?> results;

			try {
				results = evaluator.evaluate(arguments);
			} catch(Exception e){
				System.err.println("Failed to evaluate row " + (row) + " (" + arguments + ")");

				throw e;
			}

			// The target value is a subclass of org.jpmml.evaluator.Computable,
			// and may expose different aspects of the prediction process by implementing org.jpmml.evaluator.ResultFeature subinterfaces.
			// The collection of exposed aspects depends on the model type, and evaluator run-time configuration
			// (exposing more aspects has slight memory/performance penalty, but nothing too serious).
			Object targetValue = results.get(targetField.getName());
			//System.out.println(targetValue);

			// Marker interface for segmentation (aka ensemble) models
			HasSegmentation hasSegmentation = (HasSegmentation)targetValue;

			Collection<? extends SegmentResult> segmentResults = hasSegmentation.getSegmentResults();
			for(SegmentResult segmentResult : segmentResults){
				Object segmentTargetValue = segmentResult.getTargetValue();
				Number segmentWeight = segmentResult.getWeight();

				// Marker interface for tree models
				HasDecisionPath hasDecisionPath = (HasDecisionPath)segmentTargetValue;

				List<Node> decisionPathNodes = hasDecisionPath.getDecisionPath();
				for(Node decisionPathNode : decisionPathNodes){
					Number recordCount = decisionPathNode.getRecordCount();

					if(recordCount == null){
						recordCount = 1;
					} else

					{
						recordCount = (recordCount.intValue() + 1);
					}

					decisionPathNode.setRecordCount(recordCount);

					MiningFunction miningFunction = model.getMiningFunction();
					switch(miningFunction){
						case REGRESSION:
							break;
						case CLASSIFICATION:
							{
								ScoreDistribution scoreDistribution = ensureScoreDistribution(decisionPathNode, expectedTargetValue);

								scoreDistribution.setRecordCount((scoreDistribution.getRecordCount()).intValue() + 1);
							}
							break;
						default:
							throw new UnsupportedAttributeException(model, miningFunction);
					}
				}
			}
		}

		reader.close();

		PMML pmml = evaluator.getPMML();

		try(OutputStream os = new FileOutputStream(this.pmmlOutputFile)){
			MetroJAXBUtil.marshalPMML(pmml, os);
		}
	}

	static
	private ModelEvaluator<?> loadModelEvaluator(File pmmlFile) throws Exception {
		// By default, the underlying JPMML-Model library optimizes the representation of decision tree models for memory-efficiency.
		// Memory-efficient Node subclasses are read-only.
		// As we are interested in rewriting parts of the decision tree data structure (reconstructing record counts at intermediate tree levels),
		// We must switch from the default optimizing node type adapter to a custom non-optimizing node type adapter.
		NodeTransformer nodeTransformer = new NodeTransformer(){

			@Override
			public Node fromComplexNode(ComplexNode complexNode){
				return complexNode;
			}

			@Override
			public ComplexNode toComplexNode(Node node){
				return (ComplexNode)node;
			}
		};

		// Set for the current thread
		NodeAdapter.NODE_TRANSFORMER_PROVIDER.set(nodeTransformer);

		// It is possible to transfor and optimize the PMML class model object
		// (that was loaded from the PMML file) by running a collection of Visitor API classes over it.
		// In the current case, we want to reconstruct all record counts from scratch,
		// therefore we are running a custom RecordCountCleaner visitor class.
		VisitorBattery visitors = new VisitorBattery();
		visitors.add(RecordCountCleaner.class);

		ModelEvaluator<?> modelEvaluator = new LoadingModelEvaluatorBuilder()
			.setLocatable(false)
			.setVisitors(visitors)
			//.setOutputFilter(OutputFilters.KEEP_FINAL_RESULTS)
			.load(pmmlFile)
			.build();

		// Unset for the current thread
		NodeAdapter.NODE_TRANSFORMER_PROVIDER.remove();

		// Perforing the self-check
		modelEvaluator.verify();

		return modelEvaluator;
	}

	static
	private ScoreDistribution ensureScoreDistribution(Node node, Object expectedTargetValue){
		List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

		for(ScoreDistribution scoreDistribution : scoreDistributions){
			Object value = scoreDistribution.getValue();

			if(expectedTargetValue.equals(value)){
				return scoreDistribution;
			}
		}

		ScoreDistribution scoreDistribution = new ScoreDistribution(expectedTargetValue, 0);

		scoreDistributions.add(scoreDistribution);

		return scoreDistribution;
	}

	private static final String CSV_SEPARATOR = ",";
}