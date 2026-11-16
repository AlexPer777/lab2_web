/** Emits real JSON encoding for an independent parser in FastCgiIntegrationTest.py. */
public class JsonStringProbe {
    public static void main(String[] args) {
        StringBuilder value = new StringBuilder("Кавычка: \"; слеш: \\");
        for (int i = 0; i < 32; i++) {
            value.append((char) i);
        }
        System.out.print(Main.jsonString(value.toString()));
    }
}
