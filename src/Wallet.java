/**
 * This class implements the wallet
 */

import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Wallet {
    private double wincoin;
    private List<Transaction> transactions;

    public Wallet() {
        this.wincoin = 0;
        transactions = new ArrayList<>();
    }

    /**
     * @return the number of wincoin in the wallet
     */
    public synchronized double getWincoin() {
        return wincoin;
    }

    /**
     * @return the list of transactions
     */
    public synchronized List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    /**
     * Adds wincoin to the wallet creating a new transaction
     * @param wincoin amount of wincoin to add
     */
    public synchronized void addWincoin(double wincoin) {
        this.wincoin += wincoin;
        transactions.add(
                new Transaction( //Creates a new transaction
                        "+" + wincoin + " wincoin",
                        new Date(System.currentTimeMillis())
                )
        );
    }

    /**
     * Converts the amount of wincoin in the wallet in BitCoin
     * @return the amount of wincoin in the wallet in BTC
     */
    public double wincoinToBTC() {
        try {
            //Creates the URL to get conversion rate
            URL url = new URL("https://www.random.org/decimal-fractions/?num=1&dec=10&col=2&format=plain&rnd=new");
            //Opens the reader stream
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            //Reads the conversion rate from the stream
            String line = reader.readLine();
            if (line == null) throw new IOException("No content received from random.org");

            double conversionRate = Double.parseDouble(line);
            synchronized (this) {
                //Calculates the BTC amount
                return wincoin * conversionRate;
            }
        } catch (MalformedURLException e) {
            System.err.println("Error with the URL: (" + e.getMessage() + ")");
            return -1;
        } catch (IOException e) {
            System.err.println("Error while communicating with random.org: (" + e.getMessage() + ")");
            return -1;
        }
    }

    /**
     * Writes the wallet to a file in json format
     * @param writer writer used to write the object as json object
     */
    public synchronized void toJsonFile(JsonWriter writer) throws IOException {
        //Writes '{'
        writer.beginObject();
        writer.name("wincoin").value(wincoin);

        writer.name("transactions");
        //Writes '['
        writer.beginArray();
        //Writes transactions in json format
        for (Transaction transaction : transactions)
            transaction.toJsonFile(writer);
        //Writes ']'
        writer.endArray();
        //Writes '}'
        writer.endObject();
        writer.flush();
    }
}
