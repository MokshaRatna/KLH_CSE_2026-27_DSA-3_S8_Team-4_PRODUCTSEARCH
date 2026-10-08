
import java.io.*;
import java.util.*;

public class ProductDataLoader {
    public static Product[] load(String file) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        ArrayList<Product> list = new ArrayList<Product>();
        while ((line = br.readLine()) != null) {
            String[] p = splitCsv(line);
            if (p.length < 12) continue;
            list.add(new Product(
                Integer.parseInt(p[0]), p[1], p[2], p[3], Double.parseDouble(p[4]),
                Double.parseDouble(p[5]), Integer.parseInt(p[6]), p[7], p[8], p[9],
                Integer.parseInt(p[10]), p[11]
            ));
        }
        br.close();
        return list.toArray(new Product[0]);
    }

    private static String[] splitCsv(String s) {
        ArrayList<String> a = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean q=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='"') { q=!q; }
            else if(c==',' && !q) { a.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        a.add(cur.toString());
        return a.toArray(new String[0]);
    }
}
