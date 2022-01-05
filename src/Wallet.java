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

    public synchronized double getWincoin() {
        return wincoin;
    }

    public synchronized List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    public synchronized void addWincoin(double wincoin) {
        this.wincoin += wincoin;
        transactions.add(
                new Transaction(
                        "+" + wincoin + " wincoin",
                        new Date(System.currentTimeMillis())
                )
        );
    }

    public double wincoinToBTC() {
        try {
            URL url = new URL("https://www.random.org/decimal-fractions/?num=1&dec=10&col=2&format=plain&rnd=new");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String line = reader.readLine();
            if (line == null) throw new IOException("No content received from random.org");

            double conversionRate = Double.parseDouble(line);
            synchronized (this) {
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

    public synchronized void toJsonFile(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("wincoin").value(wincoin);

        writer.name("transactions");
        writer.beginArray();
        for (Transaction transaction : transactions)
            transaction.toJsonFile(writer);
        writer.endArray();
        writer.endObject();
        writer.flush();
    }
}
