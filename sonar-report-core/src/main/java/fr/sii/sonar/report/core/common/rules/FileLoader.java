package fr.sii.sonar.report.core.common.rules;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;

import fr.sii.sonar.report.core.common.exception.ParseException;
import fr.sii.sonar.report.core.common.exception.RuleException;
import fr.sii.sonar.report.core.common.parser.Parser;
import fr.sii.sonar.report.core.common.rules.debt.FirstSupportingRemadiationProviderDecorator;
import fr.sii.sonar.report.core.common.rules.debt.RemediationProvider;
import fr.sii.sonar.report.core.quality.domain.rule.Debt;
import fr.sii.sonar.report.core.quality.domain.rule.RuleDefinition;

/**
 * Loader that loads rules from file.
 * 
 * <p>
 * It delegates file parsing to a {@link Parser}
 * 
 * @author Aurélien Baudet
 *
 */
public class FileLoader implements RulesDefinitionLoader {
	private static final Logger LOG = LoggerFactory.getLogger(FileLoader.class);

	private final InputStream stream;

	private final Parser<List<RuleDefinition>> parser;

	private RemediationProvider remediationProvider;

	public FileLoader(InputStream stream, Parser<List<RuleDefinition>> parser, RemediationProvider remediationProvider) {
		super();
		this.stream = stream;
		this.parser = parser;
		this.remediationProvider = remediationProvider;
	}

	public FileLoader(InputStream stream, Parser<List<RuleDefinition>> parser) {
		this(stream, parser, new FirstSupportingRemadiationProviderDecorator());
	}

	public void load(NewRepository repository) {
		LOG.info("Loading rules for " + repository.key());
		try {
			List<RuleDefinition> rules = parser.parse(stream);
			LOG.info(rules.size() + " rules defined for " + repository.key() + ". Registering them");
			for (RuleDefinition rule : rules) {
				NewRule newRule = repository.createRule(rule.getKey());
				newRule.setName(rule.getName());
				newRule.setHtmlDescription(rule.getDescription());
				if (rule.getSeverity() != null) {
					newRule.setSeverity(rule.getSeverity().toUpperCase());
				}
				if (rule.getStatus() != null) {
					newRule.setStatus(RuleStatus.valueOf(rule.getStatus()));
				}
				// manage tags
				if (rule.getTags() != null) {
					for (String tag : rule.getTags()) {
						newRule.addTags(tag);
					}
				}
				// manage debt
				Debt debt = rule.getDebt();
				if(debt != null) {
					if(debt.getSqaleSubCharacteristic()!=null) {
						newRule.setDebtSubCharacteristic(debt.getSqaleSubCharacteristic());
					}
					if(debt.getSqaleRemediation()!=null) {
						remediationProvider.setRemediation(newRule, debt.getSqaleRemediation());
					}
				}
				// TODO: manage params
			}
		} catch (ParseException e) {
			LOG.error("Failed to parse rule files", e);
			throw new RuleException("Failed to parse rule files", e);
		}
	}
}
