import java.util.*;

import com.activfinancial.contentplatform.contentgatewayapi.ContentGatewayClient;
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
import com.activfinancial.samples.common.ui.io.UiIo;
import com.activfinancial.samples.common.ui.io.UiIo.LogType;
import org.javatuples.Quartet;



public class OptionSeries extends ContentGatewayClient {

    private static final String COMMA = ",";
    final String DASH = "-";
    final String OPTION_CALL_SYMBOL = "C";

    private UiIo uiIo = new UiIo();

    private FieldListValidator fieldListValidator;

    private static final String[] osiSymbols = {

        "NVDA230915P00090000",
        "META230217C00050000",
        "GOOGL230317C00054000",
        "NVDA230317P00080000",
        "NVDA240119P00110000",
        "NVDA230317P00100000",
        "GOOGL230616C00142500",
        "NVDA240119P00235000",
        "META230217C00005000",
        "META240621P00210000"
    };

    private static Application application;

    public OptionSeries(Application application) {

        super(application);

        fieldListValidator = new FieldListValidator(this);
    }

    public static void main(String[] args) throws MiddlewareException {

        Settings settings = new Settings();
        application = new Application(settings);

        new OptionSeries(application).run();
    }

    private void run() throws MiddlewareException {

        application.startThread();
        if (!connect())
            return;

        runExample();

        application.postDiesToThreads();
        application.waitForThreadsToExit();
    }

    private boolean connect() {
        StatusCode statusCode;

        ConnectParameters connectParameters = new ConnectParameters();

        connectParameters.serviceId = "Service.ContentGateway";
        connectParameters.url = "ams://199.47.167.100:9005/ContentGateway:Service?rxCompression=Rdc";
        connectParameters.userId = "drwt1000-user11";
        connectParameters.password = "drwt-u11";

        statusCode = this.connect(connectParameters, ContentGatewayClient.DEFAULT_TIMEOUT);

        if (StatusCode.STATUS_CODE_SUCCESS != statusCode)
            uiIo.logMessage(LogType.LOG_TYPE_ERROR, "Connect() failed, error - " + statusCode.toString());

        return statusCode == StatusCode.STATUS_CODE_SUCCESS;
    }

    private void runExample() throws MiddlewareException {

        OptionSeriesFilter optionSeriesFilter = new OptionSeriesFilter();
        optionSeriesFilter.setExchangeList(Arrays.asList(Exchange.EXCHANGE_US_OPTIONS_COMPOSITE));

        for (String osiSymbol : osiSymbols) {

            final Quartet<String, String, String, String> optionsData = ActivHashCodeConversionUtility.createOptionsData(osiSymbol);

            final String symbolStr = optionsData.getValue0();
            final String expirationDateStr = optionsData.getValue1();
            final String strikePriceStr = optionsData.getValue2();
            final String optionTypeStr = optionsData.getValue3();

            setupFilter(optionSeriesFilter, expirationDateStr, strikePriceStr, optionTypeStr, osiSymbol);

            // Construct this request block just once and cache it.
            RequestBlock requestBlockOptions = new RequestBlock();

            requestBlockOptions.fieldIdList.add(FieldIds.FID_SYMBOL);
            requestBlockOptions.fieldIdList.add(FieldIds.FID_EXPIRATION_DATE);
            requestBlockOptions.fieldIdList.add(FieldIds.FID_STRIKE_PRICE);
            requestBlockOptions.fieldIdList.add(FieldIds.FID_OPTION_TYPE);
            requestBlockOptions.fieldIdList.add(FieldIds.FID_TRADE);

            List<OptionInfo> options = new ArrayList<OptionInfo>();

            // get all options for an underling
            StatusCode statusCode = GetOptionSeriesHelper.getOptionSeries(this, fieldListValidator, symbolStr, optionSeriesFilter, requestBlockOptions, options);

            if (statusCode == StatusCode.STATUS_CODE_SUCCESS) {
                for (OptionInfo optionInfo : options) {
                    // dump to the screen
                    uiIo.logMessage(LogType.LOG_TYPE_INFO, optionInfo.toString());
                }
            }
        }
        
        // now disconnect
        this.disconnect();
    }

    private void setupFilter(OptionSeriesFilter oSeriesFilter, String expDataStr, String strikePri, String optionTyp, String osiSymbol) throws MiddlewareException {

        String me = "OptionsSubscription.setupFilter() ";

        final String[] split = expDataStr.split(DASH);
        final int dateVal = Integer.parseInt(split[0]);
        final int monthVal = Integer.parseInt(split[1]);
        final int yearVal = Integer.parseInt(split[2]);

        final com.activfinancial.middleware.fieldtypes.Date date;
        try {
            date = new Date(yearVal, monthVal, dateVal);
            oSeriesFilter.setStartDate(date);
            oSeriesFilter.setEndDate(date);
        } catch (MiddlewareException e) {
            e.printStackTrace();
        }

        final double strikePrice = Double.parseDouble(strikePri);
        final Rational rational = ActivHashCodeConversionUtility.findStrikePriceRational(strikePrice);

        if (rational == null) {
            return;
        }

        oSeriesFilter.setLowStrike(rational);
        oSeriesFilter.setHighStrike(rational);

        final OptionSeriesFilter.CallPutEnum callPutEnum = optionTyp.equalsIgnoreCase(OPTION_CALL_SYMBOL) ?
                                                               OptionSeriesFilter.CallPutEnum.CALL :
                                                               OptionSeriesFilter.CallPutEnum.PUT;
        oSeriesFilter.setCallPut(callPutEnum);
    }

}
