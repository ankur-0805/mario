package com.mario.backoffice.actions.returns;

import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.actions.ActionResult.StatusFlag;
import com.hybris.cockpitng.engine.impl.AbstractComponentWidgetAdapterAware;
import com.mario.omsbackoffice.renderers.OrderEntryUsedQtyRenderer;

import de.hybris.platform.basecommerce.enums.ConsignmentStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.returns.ReturnService;
import java.util.Objects;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


public class MarioCreateReturnRequestAction extends AbstractComponentWidgetAdapterAware
		implements CockpitAction<OrderModel, OrderModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(MarioCreateReturnRequestAction.class);

	protected static final String SOCKET_OUT_CONTEXT = "createReturnRequestContext";
	@Autowired
	private ReturnService returnService;

	@Override
	public boolean canPerform(final ActionContext<OrderModel> actionContext)
	{
		final Object data = actionContext.getData();

		OrderModel order = null;
		if (data instanceof OrderModel)
		{

			order = (OrderModel) data;

		}
		callThirdPartyServiceToUpdateUsedQty(order);

		return !Objects.isNull(order) && !Objects.isNull(order.getEntries()) && !Objects.isNull(order.getConsignments())
				&& !order.getConsignments().stream().noneMatch((consignment) -> {

					return consignment.getStatus().equals(ConsignmentStatus.SHIPPED)
							|| consignment.getStatus().equals(ConsignmentStatus.PICKUP_COMPLETE);

				}) && !this.getReturnService().getAllReturnableEntries(order).isEmpty() && isReturnPossibleAdditionalCheck(order);
	}


	/**
	 * @param order
	 */
	private void callThirdPartyServiceToUpdateUsedQty(final OrderModel order)
	{

		LOG.info("Invoking Third party service to fetch Used QTY for order number: " + order.getCode());
		order.getEntries().stream().forEach((entryModel -> {
			if (entryModel.getEntryNumber().intValue() == 0 || entryModel.getEntryNumber().intValue() == 2)
			{
				entryModel.setOriginalSubscriptionId(String.valueOf(entryModel.getQuantity().intValue() - 1));
			}
			else
			{
				entryModel.setOriginalSubscriptionId("N/A");
			}

		}));


	}


	/**
	 * @param order
	 * @return
	 */
	private boolean isReturnPossibleAdditionalCheck(final OrderModel order)
	{
		if (order.getTotalPrice() != null && order.getTotalPrice().doubleValue() > 200)
		{
			return false;
		}
		return true;
	}


	public String getConfirmationMessage(final ActionContext<OrderModel> actionContext)
	{
		return null;
	}


	public boolean needsConfirmation(final ActionContext<OrderModel> actionContext)
	{
		return false;
	}

	public ActionResult<OrderModel> perform(final ActionContext<OrderModel> actionContext)
	{
		this.sendOutput("createReturnRequestContext", actionContext.getData());
		final ActionResult actionResult = new ActionResult("success");
		actionResult.getStatusFlags().add(StatusFlag.OBJECT_PERSISTED);
		return actionResult;
	}


	protected ReturnService getReturnService()
	{
		return this.returnService;
	}


	public void setReturnService(final ReturnService returnService)
	{
		this.returnService = returnService;
	}

}
