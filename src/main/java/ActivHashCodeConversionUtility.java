import com.activfinancial.contentplatform.contentgatewayapi.common.UsEquityOptionHelper;
import com.activfinancial.contentplatform.contentgatewayapi.consts.Exchange;
import com.activfinancial.middleware.activbase.MiddlewareException;
import com.activfinancial.middleware.fieldtypes.Date;
import com.activfinancial.middleware.fieldtypes.Rational;
import org.javatuples.Quartet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActivHashCodeConversionUtility {


    private static final String DASH = "-";
    private static final String OPTION_CALL_SYMBOL = "C";

    /**
     *
     * @param osiSymbol
     * @return
     * @throws MiddlewareException
     *
     * with the input parameters provided, returns the Activ type-B hashcode
     */
    public static String getActivType_B_HashCode(String osiSymbol) throws MiddlewareException {

        String me = "ActivHashCodeConversionUtility.getActivType_B_HashCode() ";

        long start = System.currentTimeMillis();

        final Quartet<String, String, String, String> optionsData = createOptionsData(osiSymbol);

        final String curSymbol = optionsData.getValue0();
        final String expirationDate = optionsData.getValue1();
        final String strikePriceStr = optionsData.getValue2();
        final String optionType = optionsData.getValue3();

        final String[] split = expirationDate.split(DASH);
        final int dateVal = Integer.parseInt(split[0]);
        final int monthVal = Integer.parseInt(split[1]);
        final int yearVal = Integer.parseInt(split[2]);

        UsEquityOptionHelper helper = new UsEquityOptionHelper();

        helper.setExchange(Exchange.EXCHANGE_US_OPTIONS_COMPOSITE);
        helper.setRoot(curSymbol);

        final UsEquityOptionHelper.OptionType optionTypeEnum = optionType.equalsIgnoreCase(OPTION_CALL_SYMBOL) ? UsEquityOptionHelper.OptionType.OPTION_TYPE_CALL : UsEquityOptionHelper.OptionType.OPTION_TYPE_PUT;
        helper.setExpirationDateAndOptionType(new Date(yearVal, monthVal, dateVal), optionTypeEnum);

        final double strikePrice = Double.parseDouble(strikePriceStr);
        final Rational strikePriceRational = findStrikePriceRational(strikePrice);

        if (strikePriceRational == null) {
            return null;
        }

        helper.setStrikePrice(strikePriceRational);

        StringBuilder sb = new StringBuilder();
        helper.serialize(sb);

        long elapsedTimeInMillis = System.currentTimeMillis() - start;

        return sb.toString();
    }

    public static Quartet<String, String, String, String> createOptionsData(String osiSymbol) {

        StringBuilder stringBuilder = new StringBuilder();

        int index = -1;

        for (int k = 0; k < osiSymbol.length(); k++) {
            if (Character.isLetter(osiSymbol.charAt(k))) {
                stringBuilder.append(osiSymbol.charAt(k));
            } else {
                index = k;
                break;
            }
        }

        String symbol = stringBuilder.toString();

        String restOfStr = osiSymbol.substring(index);

        String str = restOfStr.substring(0, 6);
        final List<String> strings = splitStringEqually(str, 2);

        String expirationDate = strings.get(2) + DASH + strings.get(1) + DASH + "20" + strings.get(0);
        String optionType = restOfStr.substring(6, 7);


        restOfStr = restOfStr.substring(7);
        final String strikePrice = String.valueOf(Double.parseDouble(restOfStr) / 1000);


        List<String> listOfFields = Arrays.asList(symbol, expirationDate, strikePrice, optionType);
        Quartet<String, String, String, String> quartet = Quartet.fromCollection(listOfFields);

        return quartet;
    }

    public static List<String> splitStringEqually(String text, int size) {
        List<String> result = new ArrayList<String>((text.length() + size - 1) / size);
        for (int i = 0; i < text.length(); i += size) {
            result.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return result;
    }

    public static String createOsiSymbolUsingType_B_activData(String typeB_data)  {

        String me = "ActivHashCodeConversionUtility.createOsiSymbolUsingType_B_activData() : ";

        try{
            UsEquityOptionHelper equityOptionHelper = new UsEquityOptionHelper(typeB_data);

            final String SYMBOL = equityOptionHelper.getRoot();

            final String expirationDate = equityOptionHelper.getExpirationDate().toString();

            final String[] split = expirationDate.split("-");
            String yearStr = split[0];
            yearStr = yearStr.substring(yearStr.length() - 2);
            final String monthStr = split[1];
            final String dateStr = split[2];

            final String EXPIRATION_DATE = yearStr + monthStr + dateStr;

            final UsEquityOptionHelper.OptionType optionType = equityOptionHelper.getOptionType();

            final String OPTION_TYPE = optionType == UsEquityOptionHelper.OptionType.OPTION_TYPE_CALL ? "C" : "P";

            final Rational rationalStrikePrice = equityOptionHelper.getStrikePrice();

            final double originalStrikePrice = rationalStrikePrice.getDouble();

            String STRIKE_PRICE = createStrikePriceWithStr(originalStrikePrice);

            final String osiSymbol = SYMBOL+EXPIRATION_DATE+OPTION_TYPE+STRIKE_PRICE;

            return osiSymbol;
        } catch (MiddlewareException ex) {
        }

        return null;
    }

    private static String createStrikePriceWithStr(double originalStrikePrice) {

        String partialStrikePrice = String.valueOf(originalStrikePrice*1000);
        final int index = partialStrikePrice.indexOf(".");

        if(index>=0){
            partialStrikePrice = partialStrikePrice.substring(0,index);
        }

        int digitsNeedToAddedForStrikePrice = 8 - partialStrikePrice.length();

        char charToAppend = '0';
        char[] charArray = new char[digitsNeedToAddedForStrikePrice];
        Arrays.fill(charArray, charToAppend);
        String newString = new String(charArray);

        return newString + partialStrikePrice;
    }


    /*
     *
     * this will find the decimal places withn examples provided below
     *
     *   123 -> 0
     *   123.0 -> 0
     *   123.00 -> 0
     *   123.50 -> 1
     *   123.5001 -> 4
     * */
    public static int findDecimalPlaces(double value) {
        if (value == (int) value) {
            return 0;
        }

        final BigDecimal bigDecimal = BigDecimal.valueOf(value);
        return bigDecimal.scale();
    }

    public static Rational findStrikePriceRational(double strikePrice) throws MiddlewareException {

        String me = "ActivHashCodeConversionUtility.findStrikePriceRational() : ";

        final int decimalPlaces = findDecimalPlaces(strikePrice);
        final BigDecimal strikePriceBigDecimal = BigDecimal.valueOf(strikePrice);

        switch (decimalPlaces) {
            case 0:
            {
                final Rational rational = new Rational(strikePriceBigDecimal.longValue(), Rational.Denominator.DENOMINATOR_WHOLE);
                return rational;
            }

            case 1:
            {
                final Rational rational = new Rational((strikePriceBigDecimal.multiply(BigDecimal.TEN)).longValue(), Rational.Denominator.DENOMINATOR_1DP);
                return rational;
            }

            case 2:
            {
                final Rational rational = new Rational((strikePriceBigDecimal.multiply(BigDecimal.valueOf(100))).longValue(), Rational.Denominator.DENOMINATOR_2DP);
                return rational;
            }

            case 3:
            {
                final Rational rational = new Rational((strikePriceBigDecimal.multiply(BigDecimal.valueOf(1000))).longValue(), Rational.Denominator.DENOMINATOR_3DP);
                return rational;
            }

            case 4:
            {
                final Rational rational = new Rational((strikePriceBigDecimal.multiply(BigDecimal.valueOf(10000))).longValue(), Rational.Denominator.DENOMINATOR_4DP);
                return rational;
            }

            default:
            {
                return null;
            }
        }
    }

    public static void main(String[] args) throws MiddlewareException {
        final Rational strikePriceRational = findStrikePriceRational(1567.78);
        System.out.println(strikePriceRational);
    }

}
