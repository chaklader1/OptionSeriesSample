/**
 * GetOptionSeriesHelper.java  Dec 7, 2007
 * 
 * Copyright 2007 ACTIV Financial Systems, Inc. All rights reserved.
 * ACTIV PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import java.util.List;

import com.activfinancial.contentplatform.contentgatewayapi.ContentGatewayClient;
import com.activfinancial.contentplatform.contentgatewayapi.FieldListValidator;
import com.activfinancial.contentplatform.contentgatewayapi.GetMatch;
import com.activfinancial.contentplatform.contentgatewayapi.GetPattern;
import com.activfinancial.contentplatform.contentgatewayapi.common.RequestBlock;
import com.activfinancial.contentplatform.contentgatewayapi.common.ResponseBlock;
import com.activfinancial.contentplatform.contentgatewayapi.common.SymbolId;
import com.activfinancial.contentplatform.contentgatewayapi.consts.FieldIds;
import com.activfinancial.contentplatform.contentgatewayapi.consts.FieldStatus;
import com.activfinancial.contentplatform.contentgatewayapi.consts.RelationshipIds;
import com.activfinancial.middleware.StatusCode;
import com.activfinancial.middleware.activbase.MiddlewareException;
import com.activfinancial.middleware.fieldtypes.FieldTypeFactory;
import com.activfinancial.middleware.fieldtypes.Rational;
import com.activfinancial.middleware.fieldtypes.TRational;

/**
 * helper class for the CG calls
 * 
 * @author Ilya Goberman
 */
public class GetOptionSeriesHelper {

    private static RequestBlock optionRootRequestBlock = new RequestBlock();
    private static RequestBlock lastTradeRequestBlock = new RequestBlock();

    static {
	    optionRootRequestBlock.relationshipId = RelationshipIds.RELATIONSHIP_ID_OPTION_ROOT;
	    optionRootRequestBlock.fieldIdList.add(FieldIds.FID_STRIKE_PRICE_LIST);
        optionRootRequestBlock.fieldIdList.add(FieldIds.FID_EXPIRATION_DATE_LIST);
	    
	    lastTradeRequestBlock.relationshipId = RelationshipIds.RELATIONSHIP_ID_NONE;
	    lastTradeRequestBlock.fieldIdList.add(FieldIds.FID_TRADE);
	    lastTradeRequestBlock.fieldIdList.add(FieldIds.FID_CLOSE);
    }
    
    /**
     * Fetches Options in two steps: Get option roots first and then construct patterns for the MultiplePatternMatchList
     * call on the options table based on the optionSeriesFilter and Option Roots returned in the first call.
     * 
     * @param client content gateway instance
     * @param fieldListValidator field list validator
     * @param symbol symbol
     * @param optionSeriesFilter filter
     * @param requestBlockOptions request block for options
     * @param options list of options returned from the call 
     * @return StatusCode
     */
    static public StatusCode getOptionSeries(ContentGatewayClient client, FieldListValidator fieldListValidator, String symbol, OptionSeriesFilter optionSeriesFilter, RequestBlock requestBlockOptions, List<OptionInfo> options) {
        if (optionSeriesFilter == null)
            throw new IllegalArgumentException("Filter should not be null.");

        // these could be fetched from the thread local storage instead of constructing them each call
        GetMatch.RequestParameters optionRootsRequestParameters = new GetMatch.RequestParameters();
        GetMatch.ResponseParameters optionRootsResponseParameters = new GetMatch.ResponseParameters();
        StatusCode statusCode;

        optionRootsRequestParameters.symbolIdList.add(new SymbolId(symbol));

        // from the listing symbol, first we need to get all the option roots. This is done using the navigation model:
        optionRootsRequestParameters.requestBlockList.add(optionRootRequestBlock);
        // fetch symbol's last trade for at the money request
        if (optionSeriesFilter.isAtTheMoney()) {
            optionRootsRequestParameters.requestBlockList.add(lastTradeRequestBlock);
        }

        // get option roots
        statusCode = ContentGatewayClient.getMatch().sendRequest(client, optionRootsRequestParameters, optionRootsResponseParameters);

        if (StatusCode.STATUS_CODE_SUCCESS == statusCode) {
            // these could be fetched from the thread local storage instead of constructing them each call
            GetPattern.RequestParameters optionsRequestParameters = new GetPattern.RequestParameters();
            GetPattern.ResponseParameters optionsResponseParameters = new GetPattern.ResponseParameters();
            
            optionsRequestParameters.requestBlockList.add(requestBlockOptions);

            optionsResponseParameters.responseBlockList.clear();

            // need last trade for At the money
            Rational []lastTrade = new Rational[1];
            
            // now retrieve all the options for all the option roots
            statusCode = getOptionsFromRoots(client, fieldListValidator, optionsRequestParameters, optionsResponseParameters, optionRootsResponseParameters.responseBlockList, optionSeriesFilter, lastTrade);
            
            if (statusCode == StatusCode.STATUS_CODE_SUCCESS) {
                List<ResponseBlock> optionsResponseBlockList = optionsResponseParameters.responseBlockList;

                for (ResponseBlock responseBlock : optionsResponseBlockList) {
                    if (responseBlock.isValidResponse()) {
                        try {
                            // keep reusing the same fieldListValidator
                            fieldListValidator.initialize(responseBlock.fieldData);

                            OptionInfo optionInfo = new OptionInfo(lastTrade[0], responseBlock.responseKey.symbol, fieldListValidator);

                            options.add(optionInfo);
                        }
                        catch (MiddlewareException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        return statusCode;
    }

    static private StatusCode getOptionsFromRoots(ContentGatewayClient client, FieldListValidator fieldListValidator, GetPattern.RequestParameters optionsRequestParameters, GetPattern.ResponseParameters optionsResponseParameters, List<ResponseBlock> optionRootsResponseBlockList, OptionSeriesFilter optionSeriesFilter, Rational[] lastTrade) {
    	// if it was at the money request, find out the last sale
    	lastTrade[0] = Rational.ZERO;
    	if (optionSeriesFilter.isAtTheMoney()) {
            for (ResponseBlock responseBlock : optionRootsResponseBlockList) {
                if (responseBlock.isValidResponse() && responseBlock.relationshipId == RelationshipIds.RELATIONSHIP_ID_NONE) {
                	try {
	                	fieldListValidator.initialize(responseBlock.fieldData);
	                	FieldListValidator.Field fieldTrade = fieldListValidator.getField(FieldIds.FID_TRADE);
	                	if (fieldTrade.fieldStatus == FieldStatus.FIELD_STATUS_DEFINED) {
	                	    TRational trade = fieldTrade.getActivFieldType(TRational.FIELD_TYPE); 
	                		lastTrade[0] = trade.getRational();
	                	}
	                	else {
	                		// default to close if last sale is 0.
		                	FieldListValidator.Field fieldClose = fieldListValidator.getField(FieldIds.FID_CLOSE);
		                	if (fieldClose.fieldStatus == FieldStatus.FIELD_STATUS_DEFINED) {
		                		lastTrade[0] = fieldClose.getActivFieldType(Rational.FIELD_TYPE);
		                	}
	                	}
	                }
	                catch (MiddlewareException e) {
	                    e.printStackTrace();
	                }
	                break;
                }
            }
            
            // clone.
            if (lastTrade[0].isInitialized()) {
                try {
                    lastTrade[0] = (Rational)FieldTypeFactory.getInstance().clone(lastTrade[0]);
                } catch (MiddlewareException e) {
                    e.printStackTrace();
                }
            }
    	}
    	
        // responseBlockList contains a list of option root symbols. To retrieve all options for these roots, we
        // make a GetPattern request, where the patterns to search for are mangled option root symbols

        // for each option root, form a pattern to search for
        optionsRequestParameters.symbolPatternList.clear();
        for (ResponseBlock responseBlock : optionRootsResponseBlockList) {
            if (responseBlock.isValidResponse() && responseBlock.relationshipId == RelationshipIds.RELATIONSHIP_ID_OPTION_ROOT) {
                // append new entries to the requestParameters.SymbolPatternList in each call
                OptionSeriesFilter.calculateOptionPatterns(fieldListValidator, responseBlock, optionSeriesFilter, optionsRequestParameters.symbolPatternList, lastTrade[0]);
            }
        }

        // get options now
        return ContentGatewayClient.getPattern().sendRequest(client, optionsRequestParameters, optionsResponseParameters);
    }
}
