import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    public double getWincoin() {
        return wincoin;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void addWincoin(double wincoin) {
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
            return wincoin * conversionRate;
        } catch (MalformedURLException e) {
            System.err.println("Error with the URL: (" + e.getMessage() + ")");
            return -1;
        } catch (IOException e) {
            System.err.println("Error while communicating with random.org: (" + e.getMessage() + ")");
            return -1;
        }
    }
}
