import java.nio.file.*;
import java.util.*;
import com.safenest.app.DomainRules;
public class BundledCatalogueCheck {
 public static void main(String[] args) throws Exception {
  Set<String> domains=new HashSet<>(Files.readAllLines(Path.of(args[0])));
  if(domains.size()!=35155) throw new AssertionError("count");
  for(String d:domains) {
   if(!d.equals(DomainRules.normalizeHostname(d))) throw new AssertionError("normalize "+d);
   if(!DomainRules.isBlocked(d,domains)||!DomainRules.isBlocked("mobile."+d,domains)) throw new AssertionError("missing "+d);
  }
  for(String h:List.of("google.com","facebook.com","m.facebook.com","youtube.com","bkash.com","nagad.com.bd","mysafenestbd.com","krikya.io.example.org","notkrikya.io"))
   if(DomainRules.isBlocked(h,domains)) throw new AssertionError("overblocking "+h);
  for(String h:List.of("krikya.io","krikya11.live","app.krikya.tech","baji.com","1xbet.com"))
   if(!DomainRules.isBlocked(h,domains)) throw new AssertionError("missing sample "+h);
  System.out.println("PASS: 35,155 roots and their subdomains; ordinary-site and hostname-boundary checks.");
 }
}
