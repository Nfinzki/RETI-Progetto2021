public class Wallet {
    private double wincoin;

    public Wallet() {
        this.wincoin = 0;
    }

    public Wallet(double wincoin) {
        this.wincoin = wincoin;
    }

    public double getWincoin() {
        return wincoin;
    }

    public void addWincoin(double wincoin) {
        this.wincoin += wincoin;
    }

    public double toBTC() {
        return 0;
    }

    public String toString() {
        return wincoin + "";
    }
}
