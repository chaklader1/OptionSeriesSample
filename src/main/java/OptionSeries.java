import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.activfinancial.contentplatform.contentgatewayapi.ContentGatewayClient;
import com.activfinancial.contentplatform.contentgatewayapi.ContentGatewayClient.ConnectParameters;
import com.activfinancial.contentplatform.contentgatewayapi.FieldListValidator;
import com.activfinancial.contentplatform.contentgatewayapi.common.RequestBlock;
import com.activfinancial.contentplatform.contentgatewayapi.consts.Exchange;
import com.activfinancial.contentplatform.contentgatewayapi.consts.FieldIds;
import com.activfinancial.middleware.StatusCode;
import com.activfinancial.middleware.activbase.MiddlewareException;
import com.activfinancial.middleware.application.Application;
import com.activfinancial.middleware.application.Settings;
import com.activfinancial.middleware.fieldtypes.Date;
import com.activfinancial.middleware.fieldtypes.Rational;
import com.activfinancial.middleware.service.FileConfiguration;
import com.activfinancial.middleware.service.ServiceApi;
import com.activfinancial.middleware.service.ServiceInstance;
import com.activfinancial.samples.common.ui.io.UiIo;
import com.activfinancial.samples.common.ui.io.UiIo.LogType;

public class OptionSeries {
	
    private UiIo uiIo = new UiIo();

    ContentGatewayClient client;
	
    public static void main(String[] args) {
        new OptionSeries().run();
    }

    private void run() {

        Settings settings = new Settings();

        Application application = new Application(settings);
        application.startThread();

        this.client = new ContentGatewayClient(application);

        if (!connect())
            return;

        runExample();

        application.postDiesToThreads();
        application.waitForThreadsToExit();
    }
    
    private void runExample() {
        // set up filter
        OptionSeriesFilter optionSeriesFilter = new OptionSeriesFilter();
        
        String symbol = setupFilter(optionSeriesFilter);

        // Construct this request block just once and cache it.
        RequestBlock requestBlockOptions = new RequestBlock();

        requestBlockOptions.fieldIdList.add(FieldIds.FID_SYMBOL);
        requestBlockOptions.fieldIdList.add(FieldIds.FID_EXPIRATION_DATE);
        requestBlockOptions.fieldIdList.add(FieldIds.FID_STRIKE_PRICE);
        requestBlockOptions.fieldIdList.add(FieldIds.FID_OPTION_TYPE);
        requestBlockOptions.fieldIdList.add(FieldIds.FID_TRADE);
        // add more fields as needed.

        // FLV could be fetched from the thread local storage instead of constructing them each call.
        // Will be using one fieldListValidator instance to minimize object construction.
        FieldListValidator fieldListValidator = new FieldListValidator(this.client);
        List<OptionInfo> options = new ArrayList<OptionInfo>(); 
        
        // get all options for an underling
        StatusCode statusCode = GetOptionSeriesHelper.getOptionSeries(this.client, fieldListValidator, symbol, optionSeriesFilter, requestBlockOptions, options);

        if (statusCode == StatusCode.STATUS_CODE_SUCCESS) {
        	for (OptionInfo optionInfo : options) {
		        // dump to the screen
		        uiIo.logMessage(LogType.LOG_TYPE_INFO, optionInfo.toString());
        	}
        }
        
        // now disconnect
        this.client.disconnect();
    }

	private String setupFilter(OptionSeriesFilter optionSeriesFilter) {
		String symbol = "META";
//        try {
//            symbol = uiIo.getString("Enter Symbol", "CAJ", true, true);
//        } catch (MiddlewareException e) {
//        }
        
//        try
//        {
//            optionSeriesFilter.setStartDate(uiIo.getFieldType("Enter Start Date", new Date(), false, false));
//        }
//        catch (MiddlewareException e) {
//            // enter pressed
//        }
//        try
//        {
//            optionSeriesFilter.setEndDate(uiIo.getFieldType("Enter End Date", new Date(), false, false));
//        }
//        catch (MiddlewareException e) {
//            // enter pressed
//        }
//        try
//        {
//            optionSeriesFilter.setLowStrike(uiIo.getFieldType("Enter Low Strike", new Rational(), false, false));
//        }
//        catch (MiddlewareException e) {
//            // enter pressed
//        }
//        try
//        {
//            optionSeriesFilter.setHighStrike(uiIo.getFieldType("Enter High Strike", new Rational(), false, false));
//        }
//        catch (MiddlewareException e) {
//            // enter pressed
//        }
        
        // both calls and puts
        optionSeriesFilter.setCallPut(OptionSeriesFilter.CallPutEnum.BOTH);
        
        // set at the money parameters
//        if (StatusCode.STATUS_CODE_SUCCESS == uiIo.getConfirmation("Fetch only 'At the money' options?", false)) {
//        	optionSeriesFilter.setAtTheMoney(true);
//            try {
//				optionSeriesFilter.setAtTheMoneyRange(uiIo.getFieldType("Enter 'At the money range'", new Rational(5), true, true));
//			} catch (MiddlewareException e) {
//	            // enter pressed
//			}
//        }
//        else {
//        	optionSeriesFilter.setAtTheMoney(false);
//        }
        optionSeriesFilter.setAtTheMoney(false);

        List<String> exchangeList = new ArrayList<String>();
        
        exchangeList.add(Exchange.EXCHANGE_US_OPTIONS_COMPOSITE);
        optionSeriesFilter.setExchangeList(exchangeList);
		return symbol;
	}

    private boolean connect() {
        StatusCode statusCode;

        ConnectParameters connectParameters = new ConnectParameters();

        connectParameters.serviceId = "Service.ContentGateway";
        connectParameters.url = "ams://199.47.167.100:9005/ContentGateway:Service?rxCompression=Rdc";
        connectParameters.userId = "drwt1000-user11";
        connectParameters.password = "drwt-u11";

        statusCode = this.client.connect(connectParameters, ContentGatewayClient.DEFAULT_TIMEOUT);

        if (StatusCode.STATUS_CODE_SUCCESS != statusCode)
            uiIo.logMessage(LogType.LOG_TYPE_ERROR, "Connect() failed, error - " + statusCode.toString());

        return statusCode == StatusCode.STATUS_CODE_SUCCESS;
    }
}
