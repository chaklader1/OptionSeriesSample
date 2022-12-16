/**
 * OptionInfo.java  Dec 10, 2007
 * 
 * Copyright 2007 ACTIV Financial Systems, Inc. All rights reserved.
 * ACTIV PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import java.util.HashMap;
import java.util.Map;

import com.activfinancial.contentplatform.contentgatewayapi.FieldListValidator;
import com.activfinancial.contentplatform.contentgatewayapi.consts.Enumerations;
import com.activfinancial.contentplatform.contentgatewayapi.consts.FieldIds;
import com.activfinancial.contentplatform.contentgatewayapi.consts.FieldStatus;
import com.activfinancial.middleware.activbase.MiddlewareException;
import com.activfinancial.middleware.fieldtypes.Date;
import com.activfinancial.middleware.fieldtypes.FieldTypeFactory;
import com.activfinancial.middleware.fieldtypes.IFieldType;
import com.activfinancial.middleware.fieldtypes.Rational;

/**
 * @author Ilya Goberman
 */
public class OptionInfo {
	
	private boolean isCall;
    private boolean inTheMoney;
	private Date expirationDate;
    private Rational strikePrice;
	private Rational lastTrade;
	
    public OptionInfo(Rational lastTrade, String symbol, FieldListValidator fieldListValidator) throws MiddlewareException {
    	this.lastTrade = lastTrade;
    	
    	FieldListValidator.Field optionType = fieldListValidator.getField(FieldIds.FID_OPTION_TYPE);
    	
        this.isCall = (optionType.fieldType.toString().equals("" + Enumerations.OPTION_TYPE_CALL));
    	
        // populate OptionInfo instance. Clone fields because we use a 
        // single FieldListValidator instance.
        for (FieldListValidator.Field field : fieldListValidator) {
            if (FieldStatus.FIELD_STATUS_DEFINED == field.fieldStatus) {
                setField(field.fieldId, FieldTypeFactory.getInstance().clone(field.fieldType));
            }
        }
        
	}
    
	public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(this.isCall ? "Call: " : "Put: ");
        
        int cnt = 0;
        for (int fieldId : fields.keySet()) {
            if (cnt != 0)
                sb.append(", ");
            sb.append(FieldIds.getUniversalFieldName(fieldId));
            sb.append(" : ");
            sb.append(fields.get(fieldId).toString());
            cnt++;
        }
        
        if (!this.lastTrade.equals(Rational.ZERO)) {
            sb.append(this.inTheMoney ? " is in the money." : " is out of the money.");
        }

        return sb.toString();
    }
    
    Map<Integer, IFieldType> fields = new HashMap<Integer, IFieldType>();

    private void setField(int fieldId, IFieldType fieldType) {
        fields.put(fieldId, fieldType);
        
        // set in the money flag.
        switch (fieldId) {
            case FieldIds.FID_STRIKE_PRICE:
                this.strikePrice = (Rational) fieldType;
    			if (!lastTrade.equals(Rational.ZERO)) {
    				Rational strikePrice = (Rational)fieldType;
    				
    				if (this.isCall) {
    					this.inTheMoney = this.lastTrade.compareTo(strikePrice) > 0;
    				}
    				else {
    					this.inTheMoney = this.lastTrade.compareTo(strikePrice) < 0;
    				}
    			}
    			break;
    			
            case FieldIds.FID_EXPIRATION_DATE:
                this.expirationDate = (Date) fieldType;
        }    
    }

    /**
     * Get expiration date
     * @return expiration date
     */
    public Date getExpirationDate() {
        return this.expirationDate;
    }
    
    /**
     * Get strike price
     * @return strike price
     */
    public Rational getStrikePrice() {
        return this.strikePrice;
    }
    
    /**
     * Is this option a call
     * @return true if this option is call
     */
    public boolean isCall() {
        return isCall;
    }

    /**
     * Is this option in the money
     * @return true if this option is in the money
     */
    public boolean isInTheMoney() {
        return inTheMoney;
    }

    /**
     * Get last trade for the underlying
     * @return last trade for the underlying
     */
    public Rational getLastTrade() {
        return lastTrade;
    }
    
}
