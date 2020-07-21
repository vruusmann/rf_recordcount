package recordcount.visitors;

import java.util.List;

import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.tree.Node;
import org.jpmml.model.visitors.AbstractVisitor;

public class RecordCountCleaner extends AbstractVisitor {

	@Override
	public VisitorAction visit(Node node){
		node.setRecordCount(0);

		if(node.hasScoreDistributions()){
			List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

			for(ScoreDistribution scoreDistribution : scoreDistributions){
				scoreDistribution
					.setRecordCount(0)
					.setProbability((node.getScore()).equals(scoreDistribution.getValue()) ? 1d : 0d)
					.setConfidence(null);
			}
		}

		return super.visit(node);
	}
}