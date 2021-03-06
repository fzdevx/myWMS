package uk.ltd.mediamagic.mywms.goodsin;

import java.beans.PropertyDescriptor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import de.linogistix.los.inventory.facade.AdviceFacade;
import de.linogistix.los.inventory.model.LOSAdvice;
import de.linogistix.los.inventory.model.LOSAdviceState;
import de.linogistix.los.inventory.query.dto.LOSAdviceTO;
import de.linogistix.los.query.BODTO;
import de.linogistix.los.query.QueryDetail;
import de.linogistix.los.query.TemplateQuery;
import de.linogistix.los.query.TemplateQueryFilter;
import de.linogistix.los.query.TemplateQueryWhereToken;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.util.StringConverter;
import uk.ltd.mediamagic.common.utils.Strings;
import uk.ltd.mediamagic.flow.crud.BODTOPlugin;
import uk.ltd.mediamagic.flow.crud.BODTOTable;
import uk.ltd.mediamagic.flow.crud.CRUDKeyUtils;
import uk.ltd.mediamagic.flow.crud.SubForm;
import uk.ltd.mediamagic.fx.MDialogs;
import uk.ltd.mediamagic.fx.action.AC;
import uk.ltd.mediamagic.fx.action.RootCommand;
import uk.ltd.mediamagic.fx.concurrent.function.BgConsumer;
import uk.ltd.mediamagic.fx.control.TableKeySelectable;
import uk.ltd.mediamagic.fx.controller.list.CellRenderer;
import uk.ltd.mediamagic.fx.controller.list.MaterialCells;
import uk.ltd.mediamagic.fx.controller.list.TextRenderer;
import uk.ltd.mediamagic.fx.converters.DateConverter;
import uk.ltd.mediamagic.fx.converters.ToStringConverter;
import uk.ltd.mediamagic.fx.data.TableKey;
import uk.ltd.mediamagic.fx.flow.ApplicationContext;
import uk.ltd.mediamagic.fx.flow.FXErrors;
import uk.ltd.mediamagic.fx.flow.Flow;
import uk.ltd.mediamagic.fx.flow.ViewContext;
import uk.ltd.mediamagic.fx.flow.ViewContextBase;
import uk.ltd.mediamagic.mywms.FlowUtils;
import uk.ltd.mediamagic.mywms.common.QueryUtils;
import uk.ltd.mediamagic.util.DateUtils;

@SubForm(
		title="Main", columns=1, 
		properties={"adviceNumber", "externalAdviceNumber", "adviceState", "itemData", "notifiedAmount", "deliveryDate"}
	)
@SubForm(
		title="Lot", columns=2, 
		properties={"lot", "expireBatch"}
	)
@SubForm(
		title="Arrival", columns=2, 
		properties={"receiptAmount", "diffAmount"}
	)

public class AdvicePlugin  extends BODTOPlugin<LOSAdvice> {
	public enum AdviceFilter {Open, Overload, All};
	public enum Action {FinishAdivce}
	
	public AdvicePlugin() {
		super(LOSAdvice.class);
	}
	
	@Override
	public String getPath() {
		return "{1, _Goods in} -> {1, _Advice}";
	}
	
	@Override
	protected StringConverter<?> getConverter(PropertyDescriptor property) {
		if ("expectedDelivery".equals(property.getName())) return new DateConverter();
		return super.getConverter(property);
	}
	
	@Override
	public Supplier<CellRenderer<BODTO<LOSAdvice>>> createTOCellFactory() {
		return TextRenderer.of(ToStringConverter.of(i -> {
			LOSAdviceTO to = (LOSAdviceTO) i;
			return String.format("%s, %s x %f", to.getName(), to.getItemData(), to.getNotifiedAmount());
		}));
	}
	
	private static Node getIcon(LOSAdvice advice) {
		BigDecimal notified = advice.getNotifiedAmount();
		BigDecimal received = advice.getReceiptAmount();
		if (notified.compareTo(BigDecimal.ZERO) <= 0) return new ProgressIndicator(1);
		ProgressIndicator pi = new ProgressIndicator(received.setScale(3, RoundingMode.HALF_DOWN).divide(notified, RoundingMode.HALF_EVEN).doubleValue());
		return pi;
	}
	
	@Override
	public Supplier<CellRenderer<LOSAdvice>> createCellFactory() {
		return MaterialCells.withDate(AdvicePlugin::getIcon, 
				s -> DateUtils.toLocalDate(s.getExpectedDelivery()), 
				s -> String.format("%s, %s", s.getItemData().getNumber(), s.getItemData().getName()),
				s -> {
					if (s.getLot() != null) {
						return String.format("%s, %Td-%<Tb-%<Ty -> %Td-%<Tb-%<Ty", s.getLot().getName(), s.getLot().getUseNotBefore(), s.getLot().getBestBeforeEnd()); 						
					}
					else {						
						return String.format("No lot information"); 						
					}
				},
				s -> Strings.format("{0} - Expected {1}, Receipt {2}", s.toUniqueString(), s.getNotifiedAmount(), s.getReceiptAmount()));
	}

	@Override
	protected void refresh(BODTOTable<LOSAdvice> source, ViewContextBase context) {
		AdviceFilter filterValue = QueryUtils.getFilter(source, AdviceFilter.Open);

		TemplateQuery template = source.createQueryTemplate();
		if (filterValue == AdviceFilter.Open) {
			TemplateQueryFilter filter = template.addNewFilter();
			filter.addWhereToken(new TemplateQueryWhereToken(
					TemplateQueryWhereToken.OPERATOR_NOT_EQUAL, "adviceState", LOSAdviceState.OVERLOAD));
			filter.addWhereToken(new TemplateQueryWhereToken(
					TemplateQueryWhereToken.OPERATOR_NOT_EQUAL, "adviceState", LOSAdviceState.FINISHED));
		}
		else if (filterValue == AdviceFilter.Overload) {
			TemplateQueryFilter filter = template.addNewFilter();
			filter.addWhereToken(new TemplateQueryWhereToken(
					TemplateQueryWhereToken.OPERATOR_EQUAL, "adviceState", LOSAdviceState.OVERLOAD));			
		}

		QueryDetail detail = source.createQueryDetail();

		source.clearTable();
		getListData(context, detail, template)
			.thenAcceptAsync(source::setLOSResultList, Platform::runLater);			
	}

	@Override
	protected BODTOTable<LOSAdvice> getTable(ViewContextBase context) {
		BODTOTable<LOSAdvice> t = super.getTable(context);
	  QueryUtils.addFilter(t, AdviceFilter.Open, () -> refresh(t, t.getContext()));
		return t;
	}

	public void finishAdvices(TableKeySelectable source, Flow flow, ViewContext context, Collection<TableKey> key) {
		boolean yes = MDialogs.create(context.getRootNode(), "Finish advices")
				.masthead("Mark the selected advices as finished?")
				.showYesNo();

		if (yes) {
			AdviceFacade adviseFacade = context.getBean(AdviceFacade.class);
			withMultiSelectionTO(context, key, adviseFacade::finishAdvise);
		}		
	}

	
	@Override
	public List<String> getTableColumns() {
		return Arrays.asList("id", 
				"name AS adviceNumber",	"client AS client.number", 
				"itemData AS itemData.number", "itemDataName AS itemData.name", 
				"lot AS lot.name", 
				"notifiedAmount", "receiptAmount", "expectedDelivery", "state");
	}
	
	@Override
	public Flow createNewFlow(ApplicationContext context) {
		return super.createNewFlow(context)
				.globalWithSelection()
					.withMultiSelection(Action.FinishAdivce, this::finishAdvices)
				.end();
	}
	
	@Override
	protected void configureCommands(RootCommand command) {
		super.configureCommands(command);
		command.add(AC.id(Action.FinishAdivce).text("Finish Advice")).end();
	}
	
}
