package com.mario.backoffice.widgets.order.createreturnrequest;

import de.hybris.platform.basecommerce.enums.RefundReason;
import de.hybris.platform.basecommerce.enums.ReturnAction;
import de.hybris.platform.basecommerce.enums.ReturnStatus;
import de.hybris.platform.commerceservices.event.CreateReturnEvent;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.omsbackoffice.widgets.returns.dtos.ReturnEntryToCreateDto;
import de.hybris.platform.refund.RefundService;
import de.hybris.platform.returns.ReturnService;
import de.hybris.platform.returns.model.RefundEntryModel;
import de.hybris.platform.returns.model.ReturnRequestModel;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.model.ModelService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.log4j.Logger;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zk.ui.event.SelectEvent;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Doublebox;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Intbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModelArray;
import org.zkoss.zul.ListModelList;
import org.zkoss.zul.Row;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.impl.InputElement;

import com.hybris.backoffice.i18n.BackofficeLocaleService;
import com.hybris.backoffice.widgets.notificationarea.event.NotificationEvent.Type;
import com.hybris.backoffice.widgets.notificationarea.event.NotificationUtils;
import com.hybris.cockpitng.annotations.SocketEvent;
import com.hybris.cockpitng.annotations.ViewEvent;
import com.hybris.cockpitng.core.events.CockpitEventQueue;
import com.hybris.cockpitng.util.DefaultWidgetController;


public class MarioCreateReturnRequestController extends DefaultWidgetController
{
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = Logger.getLogger(MarioCreateReturnRequestController.class.getName());
	protected static final String IN_SOCKET = "inputObject";
	protected static final String OUT_CONFIRM = "confirm";
	protected static final Object COMPLETED = "completed";
	protected static final int COLUMN_INDEX_RETURN_QUANTITY = 6;
	protected static final int COLUMN_INDEX_RETURN_AMOUNT = 7;
	protected static final int COLUMN_INDEX_RETURN_REASON = 8;
	protected static final int COLUMN_INDEX_RETURN_COMMENT = 9;
	private Set<ReturnEntryToCreateDto> returnEntriesToCreate;
	private OrderModel order;
	@Wire
	private Textbox orderCode;
	@Wire
	private Textbox customer;
	@Wire
	private Combobox globalReason;
	@Wire
	private Textbox globalComment;
	@Wire
	private Grid returnEntries;
	@Wire
	private Checkbox isReturnInstore;
	@Wire
	private Checkbox refundDeliveryCost;
	@Wire
	private Checkbox globalReturnEntriesSelection;
	@Wire
	private Doublebox totalRefundAmount;
	@Wire
	private Doublebox deliveryCost;
	private final List<String> refundReasons = new ArrayList();
	@WireVariable
	private ReturnService returnService;
	@WireVariable
	private RefundService refundService;
	@WireVariable
	private EventService eventService;
	@WireVariable
	private EnumerationService enumerationService;
	@WireVariable
	private ModelService modelService;
	@WireVariable
	private BackofficeLocaleService cockpitLocaleService;
	@WireVariable
	private CockpitEventQueue cockpitEventQueue;

	@SocketEvent(socketId = "inputObject")
	public void initCreateReturnRequestForm(final OrderModel inputOrder)
	{
		this.setOrder(inputOrder);
		this.refundReasons.clear();
		this.isReturnInstore.setChecked(false);
		this.refundDeliveryCost.setChecked(false);
		this.globalReturnEntriesSelection.setChecked(false);
		this.deliveryCost.setValue(this.getOrder().getDeliveryCost());
		this.refundDeliveryCost.setDisabled(
				this.getReturnService().getReturnRequests(this.getOrder().getCode()).stream().anyMatch((returnRequest) -> {
					return returnRequest.getRefundDeliveryCost().booleanValue() && returnRequest.getStatus() != ReturnStatus.CANCELED;
				}));
		this.getWidgetInstanceManager()
				.setTitle(this.getWidgetInstanceManager().getLabel("customersupportbackoffice.createreturnrequest.confirm.title")
						+ " " + this.getOrder().getCode());
		this.orderCode.setValue(this.getOrder().getCode());
		this.customer.setValue(this.getOrder().getUser().getDisplayName());
		final Locale locale = this.getCockpitLocaleService().getCurrentLocale();
		this.getEnumerationService().getEnumerationValues(RefundReason.class).forEach((reason) -> {
			this.refundReasons.add(this.getEnumerationService().getEnumerationName(reason, locale));
		});
		this.globalReason.setModel(new ListModelArray(this.refundReasons));
		final Map<AbstractOrderEntryModel, Long> returnableOrderEntries = this.getReturnService()
				.getAllReturnableEntries(inputOrder);
		this.returnEntriesToCreate = new HashSet();
		returnableOrderEntries.forEach((orderEntry, returnableQty) -> {
			this.returnEntriesToCreate.add(new ReturnEntryToCreateDto(orderEntry, returnableQty.intValue(), this.refundReasons));
		});
		this.getReturnEntries().setModel(new ListModelList(this.returnEntriesToCreate));
		this.getReturnEntries().renderAll();
		this.addListeners();
	}

	protected void addListeners()
	{
		final List rows = this.returnEntries.getRows().getChildren();
		final Iterator arg2 = rows.iterator();

		while (arg2.hasNext())
		{
			final Component row = (Component) arg2.next();
			final Iterator arg4 = row.getChildren().iterator();

			while (arg4.hasNext())
			{
				final Component myComponent = (Component) arg4.next();
				if (myComponent instanceof Combobox)
				{
					myComponent.addEventListener("onCustomChange", (event) -> {
						Events.echoEvent("onLaterCustomChange", myComponent, event.getData());
					});
					myComponent.addEventListener("onLaterCustomChange", (event) -> {
						Clients.clearWrongValue(myComponent);
						myComponent.invalidate();
						this.handleIndividualRefundReason(event);
					});
				}
				else if (myComponent instanceof Checkbox)
				{
					myComponent.addEventListener("onCheck", (event) -> {
						this.handleRow((Row) event.getTarget().getParent());
						this.calculateTotalRefundAmount();
					});
				}
				else if (myComponent instanceof Intbox)
				{
					myComponent.addEventListener("onChange", (event) -> {
						this.handleIndividualQuantityToReturn(event);
					});
				}
				else if (myComponent instanceof Doublebox)
				{
					myComponent.addEventListener("onChange", (event) -> {
						this.handleIndividualAmountToReturn(event);
					});
				}
				else if (myComponent instanceof Textbox)
				{
					myComponent.addEventListener("onChanging", (event) -> {
						this.autoSelect(event);
						((ReturnEntryToCreateDto) ((Row) event.getTarget().getParent()).getValue())
								.setRefundEntryComment(((InputEvent) event).getValue());
					});
				}
			}
		}

		this.globalReason.addEventListener("onSelect", (event) -> {
			this.handleGlobalReason(event);
		});
		this.globalComment.addEventListener("onChanging", (event) -> {
			this.handleGlobalComment(event);
		});
		this.globalReturnEntriesSelection.addEventListener("onCheck", (event) -> {
			this.selectAllEntries();
		});
		this.refundDeliveryCost.addEventListener("onClick", (event) -> {
			this.calculateTotalRefundAmount();
		});
	}

	protected void handleIndividualAmountToReturn(final Event event)
	{
		((Checkbox) event.getTarget().getParent().getChildren().iterator().next()).setChecked(true);
		final Row myRow = (Row) event.getTarget().getParent();
		final BigDecimal newAmount = new BigDecimal(Double.parseDouble(((InputEvent) event).getValue()));
		((ReturnEntryToCreateDto) myRow.getValue()).getRefundEntry().setAmount(newAmount);
		this.applyToRow(Double.valueOf(newAmount.setScale(2, RoundingMode.CEILING).doubleValue()), 7, myRow);
		this.calculateTotalRefundAmount();
	}

	protected void handleIndividualQuantityToReturn(final Event event)
	{
		this.autoSelect(event);
		final Row myRow = (Row) event.getTarget().getParent();
		final ReturnEntryToCreateDto myReturnEntry = (ReturnEntryToCreateDto) myRow.getValue();
		final int amountEntered = Integer.parseInt(((InputEvent) event).getValue());
		this.calculateRowAmount(myRow, myReturnEntry, amountEntered);
	}

	protected void handleIndividualRefundReason(final Event event)
	{
		final Optional refundReason = this.getCustomSelectedRefundReason(event);
		if (refundReason.isPresent())
		{
			this.autoSelect(event);
			((ReturnEntryToCreateDto) ((Row) event.getTarget().getParent()).getValue()).getRefundEntry()
					.setReason((RefundReason) refundReason.get());
		}

	}

	protected void handleGlobalComment(final Event event)
	{
		this.applyToGrid(((InputEvent) event).getValue(), 9);
		this.returnEntries.getRows().getChildren().stream().filter((entry) -> {
			return ((Checkbox) entry.getChildren().iterator().next()).isChecked();
		}).forEach((entry) -> {
			final ReturnEntryToCreateDto myEntry = (ReturnEntryToCreateDto) ((Row) entry).getValue();
			myEntry.setRefundEntryComment(((InputEvent) event).getValue());
		});
	}

	protected void handleGlobalReason(final Event event)
	{
		final Optional refundReason = this.getSelectedRefundReason(event);
		if (refundReason.isPresent())
		{
			this.applyToGrid(Integer.valueOf(this.getReasonIndex((RefundReason) refundReason.get())), 8);
			this.returnEntries.getRows().getChildren().stream().filter((entry) -> {
				return ((Checkbox) entry.getChildren().iterator().next()).isChecked();
			}).forEach((entry) -> {
				final ReturnEntryToCreateDto myEntry = (ReturnEntryToCreateDto) ((Row) entry).getValue();
				myEntry.getRefundEntry().setReason((RefundReason) refundReason.get());
			});
		}

	}

	protected void calculateRowAmount(final Row myRow, final ReturnEntryToCreateDto myReturnEntry, final int qtyEntered)
	{
		//TODO-Change the logic in this method to customize the value of Refund Amt.
		final BigDecimal newAmount = new BigDecimal(
				myReturnEntry.getRefundEntry().getOrderEntry().getBasePrice().doubleValue() * qtyEntered);
		myReturnEntry.setQuantityToReturn(qtyEntered);
		myReturnEntry.getRefundEntry().setAmount(newAmount);
		this.applyToRow(Double.valueOf(newAmount.setScale(2, RoundingMode.CEILING).doubleValue()), 7, myRow);
		this.calculateTotalRefundAmount();
	}

	protected void calculateTotalRefundAmount()
	{
		Double calculatedRefundAmount = Double
				.valueOf(this.refundDeliveryCost.isChecked() ? this.getOrder().getDeliveryCost().doubleValue() : 0.0D);
		calculatedRefundAmount = Double
				.valueOf(calculatedRefundAmount.doubleValue() + this.returnEntriesToCreate.stream().map((entry) -> {
					return entry.getRefundEntry().getAmount();
				}).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue());
		this.totalRefundAmount.setValue(BigDecimal.valueOf(calculatedRefundAmount.doubleValue()).doubleValue());
	}

	protected void autoSelect(final Event event)
	{
		((Checkbox) event.getTarget().getParent().getChildren().iterator().next()).setChecked(true);
	}

	protected void handleRow(final Row row)
	{
		final ReturnEntryToCreateDto myEntry = (ReturnEntryToCreateDto) row.getValue();
		if (row.getChildren().iterator().next() instanceof Checkbox)
		{
			if (!((Checkbox) row.getChildren().iterator().next()).isChecked())
			{
				this.applyToRow(Integer.valueOf(0), 6, row);
				this.applyToRow((Object) null, 8, row);
				this.applyToRow(Double.valueOf(BigDecimal.ZERO.setScale(2, RoundingMode.CEILING).doubleValue()), 7, row);
				this.applyToRow((Object) null, 9, row);
				myEntry.setQuantityToReturn(0);
				myEntry.getRefundEntry().setAmount(BigDecimal.ZERO);
				myEntry.getRefundEntry().setReason((RefundReason) null);
				myEntry.setRefundEntryComment((String) null);
			}
			else
			{
				this.applyToRow(Integer.valueOf(this.globalReason.getSelectedIndex()), 8, row);
				this.applyToRow(this.globalComment.getValue(), 9, row);
				final Optional reason = this.matchingComboboxReturnReason(
						this.globalReason.getSelectedItem() != null ? this.globalReason.getSelectedItem().getLabel() : null);
				myEntry.getRefundEntry().setReason(reason.isPresent() ? (RefundReason) reason.get() : null);
				myEntry.setRefundEntryComment(this.globalComment.getValue());
			}
		}

		this.calculateTotalRefundAmount();
	}

	protected void selectAllEntries()
	{
		this.applyToGrid(Boolean.TRUE, 0);
		final Iterator arg1 = this.returnEntries.getRows().getChildren().iterator();

		while (arg1.hasNext())
		{
			final Component row = (Component) arg1.next();
			final Component firstComponent = row.getChildren().iterator().next();
			if (firstComponent instanceof Checkbox)
			{
				((Checkbox) firstComponent).setChecked(this.globalReturnEntriesSelection.isChecked());
			}

			this.handleRow((Row) row);
			if (this.globalReturnEntriesSelection.isChecked())
			{
				final int returnableQty = Integer.parseInt(((Label) row.getChildren().get(5)).getValue());
				this.applyToRow(Integer.valueOf(returnableQty), 6, row);
				this.calculateRowAmount((Row) row, (ReturnEntryToCreateDto) ((Row) row).getValue(), returnableQty);
			}
		}

		if (this.globalReturnEntriesSelection.isChecked())
		{
			this.returnEntriesToCreate.stream().forEach((entry) -> {
				entry.setQuantityToReturn(entry.getReturnableQuantity());
			});
			this.calculateTotalRefundAmount();
		}

	}

	protected int getReasonIndex(final RefundReason refundReason)
	{
		int index = 0;
		final String myReason = this.getEnumerationService().getEnumerationName(refundReason,
				this.getCockpitLocaleService().getCurrentLocale());

		for (final Iterator arg4 = this.refundReasons.iterator(); arg4.hasNext(); ++index)
		{
			final String reason = (String) arg4.next();
			if (myReason.equals(reason))
			{
				break;
			}
		}

		return index;
	}

	protected Optional<RefundReason> getSelectedRefundReason(final Event event)
	{
		Optional result = Optional.empty();
		if (!((SelectEvent) event).getSelectedItems().isEmpty())
		{
			final Object selectedValue = ((Comboitem) ((SelectEvent) event).getSelectedItems().iterator().next()).getValue();
			result = this.matchingComboboxReturnReason(selectedValue.toString());
		}

		return result;
	}

	protected Optional<RefundReason> getCustomSelectedRefundReason(final Event event)
	{
		Optional reason = Optional.empty();
		if (event.getTarget() instanceof Combobox)
		{
			final Object selectedValue = event.getData();
			reason = this.matchingComboboxReturnReason(selectedValue.toString());
		}

		return reason;
	}

	protected void applyToGrid(final Object data, final int childrenIndex)
	{
		final Iterator arg3 = this.returnEntries.getRows().getChildren().iterator();

		while (arg3.hasNext())
		{
			final Component row = (Component) arg3.next();
			final Component firstComponent = row.getChildren().iterator().next();
			if (firstComponent instanceof Checkbox && ((Checkbox) firstComponent).isChecked())
			{
				this.applyToRow(data, childrenIndex, row);
			}
		}

	}

	protected void applyToRow(final Object data, final int childrenIndex, final Component row)
	{
		int index = 0;

		for (final Iterator arg5 = row.getChildren().iterator(); arg5.hasNext(); ++index)
		{
			final Component myComponent = (Component) arg5.next();
			if (index == childrenIndex)
			{
				if (myComponent instanceof Checkbox && data != null)
				{
					((Checkbox) myComponent).setChecked(((Boolean) data).booleanValue());
				}

				if (myComponent instanceof Combobox)
				{
					if (data != null && data instanceof Integer)
					{
						((Combobox) myComponent).setSelectedIndex(((Integer) data).intValue());
					}
					else
					{
						((Combobox) myComponent).setSelectedItem((Comboitem) null);
					}
				}
				else if (myComponent instanceof Intbox)
				{
					((Intbox) myComponent).setValue((Integer) data);
				}
				else if (myComponent instanceof Doublebox)
				{
					((Doublebox) myComponent).setValue((Double) data);
				}
				else if (myComponent instanceof Textbox)
				{
					((Textbox) myComponent).setValue((String) data);
				}
			}
		}

	}

	@ViewEvent(componentID = "resetcreatereturnrequest", eventName = "onClick")
	public void reset()
	{
		this.globalReason.setSelectedItem((Comboitem) null);
		this.globalComment.setValue("");
		this.initCreateReturnRequestForm(this.getOrder());
		this.calculateTotalRefundAmount();
	}

	@ViewEvent(componentID = "confirmcreatereturnrequest", eventName = "onClick")
	public void confirmCreation() throws InterruptedException
	{
		this.validateRequest();

		try
		{
			final ReturnRequestModel e = this.getReturnService().createReturnRequest(this.getOrder());
			e.setRefundDeliveryCost(Boolean.valueOf(this.refundDeliveryCost.isChecked()));
			final ReturnStatus status = this.isReturnInstore.isChecked() ? ReturnStatus.RECEIVED : ReturnStatus.APPROVAL_PENDING;
			e.setStatus(status);
			this.getModelService().save(e);
			this.returnEntriesToCreate.stream().filter((entry) -> {
				return entry.getQuantityToReturn() != 0;
			}).forEach((entry) -> {
				this.createRefundWithCustomAmount(e, entry);
			});
			final CreateReturnEvent createReturnEvent = new CreateReturnEvent();
			createReturnEvent.setReturnRequest(e);
			this.getEventService().publishEvent(createReturnEvent);

			try
			{
				this.getRefundService().apply(e.getOrder(), e);
			}
			catch (final IllegalStateException arg3)
			{
				LOG.info("Order " + this.getOrder().getCode() + " Return record already in progress");
			}

			NotificationUtils.notifyUser(
					this.getWidgetInstanceManager().getLabel("customersupportbackoffice.createreturnrequest.confirm.success") + " - "
							+ e.getRMA(),
					Type.SUCCESS);
		}
		catch (final Exception arg4)
		{
			LOG.info(arg4.getMessage(), arg4);
			NotificationUtils.notifyUser(
					this.getWidgetInstanceManager().getLabel("customersupportbackoffice.createreturnrequest.confirm.error"),
					Type.FAILURE);
		}

		this.sendOutput("confirm", COMPLETED);
	}

	protected RefundEntryModel createRefundWithCustomAmount(final ReturnRequestModel returnRequest,
			final ReturnEntryToCreateDto entry)
	{
		final ReturnAction actionToExecute = this.isReturnInstore.isChecked() ? ReturnAction.IMMEDIATE : ReturnAction.HOLD;
		final RefundEntryModel refundEntryToBeCreated = this.getReturnService().createRefund(returnRequest,
				entry.getRefundEntry().getOrderEntry(), entry.getRefundEntryComment(), Long.valueOf(entry.getQuantityToReturn()),
				actionToExecute, entry.getRefundEntry().getReason());
		refundEntryToBeCreated.setAmount(entry.getRefundEntry().getAmount());
		this.getModelService().save(refundEntryToBeCreated);
		return refundEntryToBeCreated;
	}

	protected void validateReturnEntry(final ReturnEntryToCreateDto entry)
	{
		InputElement combobox1;
		if (entry.getQuantityToReturn() > entry.getReturnableQuantity())
		{
			combobox1 = (InputElement) this
					.targetFieldToApplyValidation(entry.getRefundEntry().getOrderEntry().getProduct().getCode(), 1, 6);
			throw new WrongValueException(combobox1,
					this.getLabel("customersupportbackoffice.createreturnrequest.validation.invalid.quantity"));
		}
		else if (entry.getRefundEntry().getReason() != null && entry.getQuantityToReturn() == 0)
		{
			combobox1 = (InputElement) this
					.targetFieldToApplyValidation(entry.getRefundEntry().getOrderEntry().getProduct().getCode(), 1, 6);
			throw new WrongValueException(combobox1,
					this.getLabel("customersupportbackoffice.createreturnrequest.validation.missing.quantity"));
		}
		else if (entry.getRefundEntry().getReason() == null && entry.getQuantityToReturn() > 0)
		{
			final Combobox combobox = (Combobox) this
					.targetFieldToApplyValidation(entry.getRefundEntry().getOrderEntry().getProduct().getCode(), 1, 8);
			throw new WrongValueException(combobox,
					this.getLabel("customersupportbackoffice.createreturnrequest.validation.missing.reason"));
		}
	}

	protected void validateRequest()
	{
		final Iterator arg1 = this.getReturnEntries().getRows().getChildren().iterator();

		while (arg1.hasNext())
		{
			final Component modelList = (Component) arg1.next();
			final Component firstComponent = modelList.getChildren().iterator().next();
			if (firstComponent instanceof Checkbox && ((Checkbox) firstComponent).isChecked())
			{
				final InputElement returnQty = (InputElement) modelList.getChildren().get(6);
				if (returnQty.getRawValue().equals(Integer.valueOf(0)))
				{
					throw new WrongValueException(returnQty,
							this.getLabel("customersupportbackoffice.createreturnrequest.validation.missing.quantity"));
				}
			}
		}

		final ListModelList<ReturnEntryToCreateDto> modelList1 = (ListModelList) this.getReturnEntries().getModel();
		if (modelList1.stream().allMatch((entry) -> {
			return entry.getQuantityToReturn() == 0;
		}))
		{
			throw new WrongValueException(this.globalReturnEntriesSelection,
					this.getLabel("customersupportbackoffice.createreturnrequest.validation.missing.selectedLine"));
		}
		else
		{
			modelList1.stream().forEach((entry) -> {
				this.validateReturnEntry(entry);
			});
		}
	}

	protected Component targetFieldToApplyValidation(final String stringToValidate, final int indexLabelToCheck,
			final int indexTargetComponent)
	{
		final Iterator arg4 = this.returnEntries.getRows().getChildren().iterator();

		while (arg4.hasNext())
		{
			final Component component = (Component) arg4.next();
			final Label label = (Label) component.getChildren().get(indexLabelToCheck);
			if (label.getValue().equals(stringToValidate))
			{
				return component.getChildren().get(indexTargetComponent);
			}
		}

		return null;
	}

	protected Optional<RefundReason> matchingComboboxReturnReason(final String refundReasonLabel)
	{
		return this.getEnumerationService().getEnumerationValues(RefundReason.class).stream().filter((reason) -> {
			return this.getEnumerationService().getEnumerationName(reason, this.getCockpitLocaleService().getCurrentLocale())
					.equals(refundReasonLabel);
		}).findFirst();
	}

	protected OrderModel getOrder()
	{
		return this.order;
	}

	public void setOrder(final OrderModel order)
	{
		this.order = order;
	}

	public Grid getReturnEntries()
	{
		return this.returnEntries;
	}

	public void setReturnEntries(final Grid returnEntries)
	{
		this.returnEntries = returnEntries;
	}

	public ReturnService getReturnService()
	{
		return this.returnService;
	}

	public EventService getEventService()
	{
		return this.eventService;
	}

	public void setEventService(final EventService eventService)
	{
		this.eventService = eventService;
	}

	public void setReturnService(final ReturnService returnService)
	{
		this.returnService = returnService;
	}

	protected EnumerationService getEnumerationService()
	{
		return this.enumerationService;
	}

	public void setEnumerationService(final EnumerationService enumerationService)
	{
		this.enumerationService = enumerationService;
	}

	protected ModelService getModelService()
	{
		return this.modelService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	protected BackofficeLocaleService getCockpitLocaleService()
	{
		return this.cockpitLocaleService;
	}

	public void setCockpitLocaleService(final BackofficeLocaleService cockpitLocaleService)
	{
		this.cockpitLocaleService = cockpitLocaleService;
	}

	protected CockpitEventQueue getCockpitEventQueue()
	{
		return this.cockpitEventQueue;
	}

	public void setCockpitEventQueue(final CockpitEventQueue cockpitEventQueue)
	{
		this.cockpitEventQueue = cockpitEventQueue;
	}

	public RefundService getRefundService()
	{
		return this.refundService;
	}

	public void setRefundService(final RefundService refundService)
	{
		this.refundService = refundService;
	}
}