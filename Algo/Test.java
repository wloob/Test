import java.util.ArrayList;
import java.util.Random;

public class Test {

    public static void main(String[] args) {
        //fullSortingTest(10000, 10);
        sortingTest(Sorter.Algorithm.QUICK_SORT, Sorter.Order.ASC, 1000000, 10);
    }

    private static void sortingTest(Sorter.Algorithm alg, Sorter.Order ord, int listSize, int stringLength) {
        ArrayList<String> list = random(listSize, stringLength);
        Sorter sorter = new Sorter<String>(list, alg);
        Timer timer = new Timer();

        timer.start(String.format("%s <%s> [%d]:%d", alg, ord, listSize, stringLength));
        sorter.sort();
        timer.stop();

        System.out.println("  Sorted? " + (sorter.isSorted() ? "YES" : "NO"));
    }

    private static void fullSortingTest(int listSize, int stringLength) {
        for (Sorter.Algorithm alg : Sorter.Algorithm.values()) {
            for (Sorter.Order ord : Sorter.Order.values()) {
                sortingTest(alg, ord, listSize, stringLength);
            }
        }
    }

    private static ArrayList<String> random(int amount, int length) {
        ArrayList<String> list = new ArrayList<>();

        for (int i = 0; i < amount; i++)
            list.add(randomString(length));

        return list;
    }

    private static String randomString(int length) {

        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }

        return buffer.toString();
    }
}