/**
 * OptionSeriesFilter.java  Dec 7, 2007
 * 
 * Copyright 2007 ACTIV Financial Systems, Inc. All rights reserved.
 * ACTIV PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import java.util.ArrayList;
import java.util.List;

import com.activfinancial.contentplatform.contentgatewayapi.FieldListValidator;
import com.activfinancial.contentplatform.contentgatewayapi.common.ResponseBlock;
import com.activfinancial.contentplatform.contentgatewayapi.common.SymbolId;
import com.activfinancial.contentplatform.contentgatewayapi.common.UsEquityOptionHelper;
import com.activfinancial.contentplatform.contentgatewayapi.common.UsEquityOptionHelper.OptionType;
import com.activfinancial.contentplatform.contentgatewayapi.consts.FieldIds;
import com.activfinancial.contentplatform.contentgatewayapi.consts.FieldStatus;
import com.activfinancial.contentplatform.contentgatewayapi.consts.TableNumbers;
import com.activfinancial.middleware.StatusCode;
import com.activfinancial.middleware.activbase.MiddlewareException;
import com.activfinancial.middleware.fieldtypes.BinaryString;
import com.activfinancial.middleware.fieldtypes.Blob;
import com.activfinancial.middleware.fieldtypes.Date;
import com.activfinancial.middleware.fieldtypes.Rational;

/**
 * filter for the OptionInfo class
 * 
 * @author Ilya Goberman
 */
public class OptionSeriesFilter {
    public enum CallPutEnum { CALL, PUT, BOTH }

    // Start expiration date
    private Date startDate;
    
    // End expiration date
    private Date endDate;
    
    // Low strike price
    private Rational lowStrike;
    
    // High strike price
    private Rational highStrike;
    
    private CallPutEnum callPut = CallPutEnum.BOTH;

    // Exchange list
    private List<String> exchangeList;
    
    // fetch at the money options only
    private boolean atTheMoney;
    
	// range for at the money options
    private Rational atTheMoneyRange;
    
    public CallPutEnum getCallPut() {
        return callPut;
    }

    public void setCallPut(CallPutEnum callPut) {
        this.callPut = callPut;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Rational getHighStrike() {
        return highStrike;
    }

    public void setHighStrike(Rational highStrike) {
        this.highStrike = highStrike;
    }

    public Rational getLowStrike() {
        return lowStrike;
    }

    public void setLowStrike(Rational lowStrike) {
        this.lowStrike = lowStrike;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public List<String> getExchangeList() {
        return exchangeList;
    }

    public void setExchangeList(List<String> exchangeList) {
        this.exchangeList = exchangeList;
    }
    
    public boolean isAtTheMoney() {
		return atTheMoney;
	}

	public void setAtTheMoney(boolean atTheMoney) {
		this.atTheMoney = atTheMoney;
	}

	public Rational getAtTheMoneyRange() {
		return atTheMoneyRange;
	}

	public void setAtTheMoneyRange(Rational atTheMoneyRange) {
		this.atTheMoneyRange = atTheMoneyRange;
	}

    
    
    /**
     * Populate patternList with with symbol patterns based on the optionSeriesFilter values.
     * 
     * @param fieldListValidator field list validator
     * @param responseBlock responseBlock of the OptionRoot request
     * @param optionSeriesFilter filter
     * @param patternList list of patterns to return
     * @param lastTrade last trade
     * @return StatusCodeSuccess if at least one item was added to the patternList
     */
    static public StatusCode calculateOptionPatterns(FieldListValidator fieldListValidator, ResponseBlock responseBlock, OptionSeriesFilter optionSeriesFilter, List<SymbolId> patternList, Rational lastTrade) {
        StatusCode statusCode = StatusCode.STATUS_CODE_FAILURE;
        
        // table number assigned in the CalculateOptionPatterns
        final char tableNumber = TableNumbers.TABLE_NO_NA_EQUITY_OPTION_ALIAS;
        
        // we can't filter until all contracts have been returned, since the expiration / strike information is not in the root.
        StringBuilder optionRootBase = new StringBuilder(), exchange = new StringBuilder();
        StatusCode statusCodeCrackOptionRoot = crackOptionRoot(responseBlock.responseKey.symbol, optionRootBase, exchange);
        if (statusCodeCrackOptionRoot != StatusCode.STATUS_CODE_SUCCESS) {
            return statusCodeCrackOptionRoot;
        }

        // Access the wrapped string list object.
        String root = optionRootBase.toString();

        // a little shortcut
        if (optionSeriesFilter.getCallPut() == CallPutEnum.BOTH && 
            optionSeriesFilter.getStartDate() == null &&
            optionSeriesFilter.getEndDate() == null && 
            optionSeriesFilter.getLowStrike() == null &&
            optionSeriesFilter.getHighStrike() == null &&
            optionSeriesFilter.isAtTheMoney() == false) {
            
            List<String> exchangeFilterList = optionSeriesFilter.getExchangeList();
            if (exchangeFilterList.size() == 0) {
                String pattern = root + '/' + '*';
                patternList.add(new SymbolId(tableNumber, pattern));
            }
            else {
                for (String exchangeCode : exchangeFilterList) {
                    String pattern = root + '/' + '*' + '.' + exchangeCode;
                    patternList.add(new SymbolId(tableNumber, pattern));
                }
            }
        }
        else {
            try {
                fieldListValidator.initialize(responseBlock.fieldData);
            }
            catch (MiddlewareException e) {
                return e.getStatusCode();
            }
            
            //Construct a list of expiration dates and strikes that fall within the bounds of the filter.
            List<Date> expirationDates = new ArrayList<Date>();
            List<Rational> strikePrices = new ArrayList<Rational>();
            
            List<Date> rootExpirationDates = new ArrayList<Date>();
            try {
                extractRootExpirationDates(fieldListValidator, rootExpirationDates);
            } 
            catch (MiddlewareException e1) {
                return e1.getStatusCode();
            }
            
            for (Date rootExpirationDate : rootExpirationDates) {
                doFilterExpirationDate(rootExpirationDate, optionSeriesFilter, expirationDates);
            }
            
            List<Rational> rootStrikePrices = new ArrayList<Rational>();
            try {
                extractRootStrikePrices(fieldListValidator, rootStrikePrices);
            } 
            catch (MiddlewareException e1) {
                return e1.getStatusCode();
            }
            
            for (Rational rootStrikePrice : rootStrikePrices) {
                doFilterStrikePrice(rootStrikePrice, optionSeriesFilter, strikePrices, lastTrade);
            }
            
            //The terms to provide to GetMultiplPatternMatch are of the form:
            //<OCC ROOT>/<Expiration Code>/<Strike Code>.<Exchange Code>
            //The above loop gave us the set of expiration and strike codes, so now we should generate a pattern for each
            //exchange specified in the filter.  The root is the responseKey in our responseBlock.
            List<String> exchangeFilterList = optionSeriesFilter.getExchangeList();
            
            StringBuilder sbPattern = new StringBuilder();
            
            for (Date expirationDate : expirationDates) {
                for (Rational strikePrice : strikePrices) {
                    try {
                        if (0 == exchangeFilterList.size()) {
                            if (optionSeriesFilter.callPut == CallPutEnum.BOTH || optionSeriesFilter.callPut == CallPutEnum.CALL) {
                                constructAliasPattern(patternList, root, sbPattern, expirationDate, OptionType.OPTION_TYPE_CALL, strikePrice, "*");
                                patternList.add(new SymbolId(tableNumber, sbPattern.toString()));
                            }
    
                            if (optionSeriesFilter.callPut == CallPutEnum.BOTH || optionSeriesFilter.callPut == CallPutEnum.PUT) {
                                constructAliasPattern(patternList, root, sbPattern, expirationDate, OptionType.OPTION_TYPE_PUT, strikePrice, "*");
                                patternList.add(new SymbolId(tableNumber, sbPattern.toString()));
                            }
                        }
                        else {
                            for (String exchangeCode : exchangeFilterList) {
                                if (optionSeriesFilter.callPut == CallPutEnum.BOTH || optionSeriesFilter.callPut == CallPutEnum.CALL) {
                                    constructAliasPattern(patternList, root, sbPattern, expirationDate, OptionType.OPTION_TYPE_CALL, strikePrice, exchangeCode);
                                    patternList.add(new SymbolId(tableNumber, sbPattern.toString()));
                                }
                                
                                if (optionSeriesFilter.callPut == CallPutEnum.BOTH || optionSeriesFilter.callPut == CallPutEnum.PUT) {
                                    constructAliasPattern(patternList, root, sbPattern, expirationDate, OptionType.OPTION_TYPE_PUT, strikePrice, exchangeCode);
                                    patternList.add(new SymbolId(tableNumber, sbPattern.toString()));
                                }
                            }
                        }
                    }
                    catch (MiddlewareException e) {
                        return e.getStatusCode();
                    }

                    //at least one pattern is required before we regard the process as having succeeded.
                    statusCode = StatusCode.STATUS_CODE_SUCCESS;
                }
            }
        }        

        return statusCode;
    }

    // extract prices from the blob in FID_STRIKE_PRICE_LIST
    private static void extractRootStrikePrices(FieldListValidator fieldListValidator, List<Rational> rootStrikePrices) throws MiddlewareException {
        FieldListValidator.Field strikePricesField = fieldListValidator.getField(FieldIds.FID_STRIKE_PRICE_LIST);
        
        // check if field status is FieldStatusDefined to avoid catching exception
        if (strikePricesField != null && strikePricesField.fieldStatus == FieldStatus.FIELD_STATUS_DEFINED)
        {
            Blob srikePricesBlob = (Blob) strikePricesField.fieldType;

            UsEquityOptionHelper.getStrikePriceList(srikePricesBlob, rootStrikePrices);
        }
    }

    // extract expiration dates in FID_EXPIRATION_DATE_LIST
    private static void extractRootExpirationDates(FieldListValidator fieldListValidator, List<Date> rootExpirationDates) throws MiddlewareException {
        FieldListValidator.Field expirationDatesField = fieldListValidator.getField(FieldIds.FID_EXPIRATION_DATE_LIST);
        
        // check if field status is FieldStatusDefined to avoid catching exception
        if (expirationDatesField != null && expirationDatesField.fieldStatus == FieldStatus.FIELD_STATUS_DEFINED)
        {
            BinaryString expirationDatesBinaryString = (BinaryString) expirationDatesField.fieldType;

            UsEquityOptionHelper.getExpirationDateList(expirationDatesBinaryString, rootExpirationDates);
        }
    }

    private static void constructAliasPattern(List<SymbolId> patternList, String root, StringBuilder sbPattern, Date expirationDate, OptionType optionType, Rational strikePrice, String exchangeCode) throws MiddlewareException {
        sbPattern.setLength(0);
        UsEquityOptionHelper.buildAliasSymbol(sbPattern, root, expirationDate, optionType, strikePrice, exchangeCode);
    }

    static private StatusCode crackOptionRoot(String rootSymbol, StringBuilder optionRootBase, StringBuilder exchange) {
        int i = rootSymbol.lastIndexOf('.');
        if (-1 == i)
            return StatusCode.STATUS_CODE_INVALID_PARAMETER;

        optionRootBase.append(rootSymbol.substring(0, i));

        if (i == rootSymbol.length()) {
            return StatusCode.STATUS_CODE_SUCCESS;
        }

        exchange.append(rootSymbol.substring(i + 1));

        return StatusCode.STATUS_CODE_SUCCESS;
    }
    
    // Utility to do the actual expiration date comparison
    static StatusCode doFilterExpirationDate(Date expirationDate, OptionSeriesFilter optionSeriesFilter, List<Date> expirationDates) {
        // First check that the date is within the bounds of the filter.
        Date startDate = optionSeriesFilter.getStartDate();
        Date endDate = optionSeriesFilter.getEndDate();

        if ((startDate != null && startDate.isInitialized()) || (endDate != null && endDate.isInitialized())) {
            if (endDate != null && endDate.isInitialized()) {
                if (expirationDate.compareTo(endDate) > 0)
                    return StatusCode.STATUS_CODE_OUT_OF_RANGE;
            }

            if (startDate != null && startDate.isInitialized()) {
                if (expirationDate.compareTo(startDate) < 0)
                    return StatusCode.STATUS_CODE_OUT_OF_RANGE;
            }
        }
        
        expirationDates.add(expirationDate);

        return StatusCode.STATUS_CODE_SUCCESS;
    }
    
    static private StatusCode doFilterStrikePrice(Rational strikePrice, OptionSeriesFilter optionSeriesFilter, List<Rational> strikePrices, Rational lastTrade) {
        Rational lower = optionSeriesFilter.getLowStrike();
        Rational upper = optionSeriesFilter.getHighStrike();

        if ((upper != null && upper.isInitialized()) || (lower != null && lower.isInitialized())) {

            if (upper != null && upper.isInitialized()) {
                if (strikePrice.compareTo(upper) > 0)
                    return StatusCode.STATUS_CODE_OUT_OF_RANGE;
            }

            if (lower != null && lower.isInitialized()) {
                if (strikePrice.compareTo(lower) < 0)
                    return StatusCode.STATUS_CODE_OUT_OF_RANGE;
            }
        }
        
        if (optionSeriesFilter.isAtTheMoney() && !optionSeriesFilter.getAtTheMoneyRange().equals(Rational.ZERO) && !lastTrade.equals(Rational.ZERO)) {
            if (Math.abs(strikePrice.getDouble() - lastTrade.getDouble()) > optionSeriesFilter.getAtTheMoneyRange().getDouble())
                return StatusCode.STATUS_CODE_OUT_OF_RANGE;
        }

        strikePrices.add(strikePrice);
        
        return StatusCode.STATUS_CODE_SUCCESS;
    }
}
