
public class KMP {
    public static int[] buildLPS(String pattern) {
        int[] lps = new int[pattern.length()];
        int len = 0, i = 1;
        while (i < pattern.length()) {
            if (pattern.charAt(i) == pattern.charAt(len)) lps[i++] = ++len;
            else if (len > 0) len = lps[len - 1];
            else lps[i++] = 0;
        }
        return lps;
    }

    public static boolean contains(String text, String pattern) {
        if(pattern == null || pattern.length()==0) return true;
        text = text.toLowerCase();
        pattern = pattern.toLowerCase();
        int[] lps = buildLPS(pattern);
        int i=0,j=0;
        while(i<text.length()) {
            if(text.charAt(i)==pattern.charAt(j)){i++;j++; if(j==pattern.length()) return true;}
            else if(j>0) j=lps[j-1];
            else i++;
        }
        return false;
    }
}
