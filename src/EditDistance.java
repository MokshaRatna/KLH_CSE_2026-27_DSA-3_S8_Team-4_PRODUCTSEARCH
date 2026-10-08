
public class EditDistance {
    public static int distance(String a, String b) {
        a=a.toLowerCase(); b=b.toLowerCase();
        int[] prev = new int[b.length()+1];
        int[] cur = new int[b.length()+1];
        for(int j=0;j<=b.length();j++) prev[j]=j;
        for(int i=1;i<=a.length();i++){
            cur[0]=i;
            for(int j=1;j<=b.length();j++){
                int cost = a.charAt(i-1)==b.charAt(j-1)?0:1;
                int x=prev[j]+1, y=cur[j-1]+1, z=prev[j-1]+cost;
                cur[j]=Math.min(x, Math.min(y,z));
            }
            int[] t=prev; prev=cur; cur=t;
        }
        return prev[b.length()];
    }
}
