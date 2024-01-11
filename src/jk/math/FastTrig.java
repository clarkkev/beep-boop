package jk.math;

public class FastTrig {
   public static final double PI        = 3.1415926535897932384626433832795D;
   public static final double TWO_PI    = 6.2831853071795864769252867665590D;
   public static final double HALF_PI   = 1.5707963267948966192313216916398D;
   public static final double QUARTER_PI = 0.7853981633974483096156608458199D;
   public static final double THREE_OVER_TWO_PI
   = 4.7123889803846898576939650749193D;


   public static void main(String[] args){
   //accuracy test
      double maxdiff = 0;
      double sum = 0;
      double dv = 0;
      for(int i = 0; i < 500000; i++){
         double p = (i-250000)*(1.571/250000);
         double diff = Math.abs((Math.atan(p) - atan(p)));
         sum += diff;
         if(diff > maxdiff){
            maxdiff = diff;
            dv = p;
         }
      }
      System.out.println(maxdiff);
      System.out.println(dv);
      System.out.println(sum);
   }
   public static final double sin(double d) {
      d += Math.PI;
      double x2 = Math.floor(d*(1/(2*Math.PI)));
      d -= x2*(2*Math.PI);
      d-=Math.PI;
   
      x2 = d * d;
      
   
      return //accurate to 6.82e-8, 3.3x faster than Math.sin, 
         //faster than lookup table in real-world conditions due to no cache misses
         //all values from "Fast Polynomial Approximations to Sine and Cosine", Garret, C. K., 2012
         (((((-2.05342856289746600727e-08*x2 + 2.70405218307799040084e-06)*x2
         - 1.98125763417806681909e-04)*x2 + 8.33255814755188010464e-03)*x2
         - 1.66665772196961623983e-01)*x2 + 9.99999707044156546685e-01)*d;
   }


   public static final double cos(double d) {
      d += Math.PI;
      double x2 = Math.floor(d*(1/(2*Math.PI)));
      d -= x2*(2*Math.PI);
      d-=Math.PI;
   
      d *= d;
   
      return //max error 5.6e-7, 4x faster than Math.cos, 
         //faster than lookup table in real-world conditions due to less cache misses
         //all values from "Fast Polynomial Approximations to Sine and Cosine", Garret, C. K., 2012
         ((((- 2.21941782786353727022e-07*d + 2.42532401381033027481e-05)*d
         - 1.38627507062573673756e-03)*d + 4.16610337354021107429e-02)*d
         - 4.99995582499065048420e-01)*d + 1;
   }
 
   public static final double sinInBounds(double d) {
      double x2 = d * d;
   
      return //accurate to 6.82e-8, better than 3.3x faster than Math.sin, 
         //faster than lookup table in real-world conditions due to less cache misses
         //all values from "Fast Polynomial Approximations to Sine and Cosine", Garret, C. K., 2012
         (((((-2.05342856289746600727e-08*x2 + 2.70405218307799040084e-06)*x2
         - 1.98125763417806681909e-04)*x2 + 8.33255814755188010464e-03)*x2
         - 1.66665772196961623983e-01)*x2 + 9.99999707044156546685e-01)*d;
   }

   public static final double cosInBounds(double d) {
   
      d *= d;
   
      return //max error 5.6e-7, better than 4x faster than Math.cos, 
         //faster than lookup table in real-world conditions due to less cache misses
         //all values from "Fast Polynomial Approximations to Sine and Cosine", Garret, C. K., 2012
         ((((- 2.21941782786353727022e-07*d + 2.42532401381033027481e-05)*d
         - 1.38627507062573673756e-03)*d + 4.16610337354021107429e-02)*d
         - 4.99995582499065048420e-01)*d + 9.99999443739537210853e-01;
   }

//Takes about 1/3 of the time of Math.tan
//chebyshev polynomials calculated with http://metamerist.com/cheby/example38.htm
   public static final double tan(double x) {
      x += HALF_PI;
      double i = Math.floor(x*(1/Math.PI));
      x -= i*(Math.PI) + HALF_PI;
   
      if(Math.abs(x) > 0.25*Math.PI){
         x = Math.signum(x)*HALF_PI - x;
         i = x*x;
         return  1/(x * ( 1
               + i * ( 0.33555055109755784988404247864565
               + i * ( 0.11611832804825573745353974789374
               + i * ( 0.0939748057591306776926771332915)))));
      }
   
      i = x*x;
      return  
         x * ( 1
               + i * ( 0.33555055109755784988404247864565
               + i * ( 0.11611832804825573745353974789374
               + i * ( 0.0939748057591306776926771332915))));
   
   }

   public static final double floor(double value){
      double d = (long)value;
      if(value > 0)
         return d;
      if(value == d)
         return d;
      return d - 1;
   }   

   public static final double asin(double value) {
   //return atan(x / Math.sqrt(1 - x*x));
      return HALF_PI - acos(value);
   }

   public static final double acos(double x){
      final double  a = 1.570758334, b = -0.212875075, c = 0.076897503, d = -0.020892330;
      if(x<0)
         return Math.PI-Math.sqrt(1+x)*(a-x*(b-x*(c-x*d)));
      return            Math.sqrt(1-x)*(a+x*(b+x*(c+x*d)));
   }
 

      //works on range 0 to 1 only!
   private static final double chebyshev_atan(double x) {
   // map x to range [-1, 1]
      final double xn = x * 2  -1;
   
   // return Chebyshev approximation
      return 0.46364760900080604
         + xn * ( 0.4005785601195767
         + xn * ( -0.07982248463207417
         + xn * ( -0.007789235359791622
         + xn * ( 0.00891247504621044))));
   }
 
   public static final double atan(double r) {
      if (r < 0.0)
         return -atan(-r);
      if (r > 1.0)
         return HALF_PI - chebyshev_atan(1.0 / r);
      return chebyshev_atan(r);
   }
 
   public static final double atan2(final double y, final double x) {
      if (x==0.0) {
         if (y==0.0)
            return 0.0; // should be Double.NaN but Math.atan2 returns 0.0 in this case;
         return (y > 0.0) ? HALF_PI : -HALF_PI;
      }
      double absX = Math.abs(x);
      double absY = Math.abs(y);
   
      double absAtan;
      if(absY > absX)
         absAtan = HALF_PI - chebyshev_atan(absX/absY);
      else
         absAtan = chebyshev_atan(absY/absX);
   
      if(x < 0)
         absAtan = PI - absAtan;
       
      if(y < 0)
         return -absAtan;
         
      return absAtan;
   
   }
   public static final double sqrt(double x){
      return Math.sqrt(x);
   //return x * (1.5d - 0.5*x* (x = Double.longBitsToDouble(0x5fe6ec85e7de30daL - (Double.doubleToLongBits(x)>>1) )) *x) * x;
   }
   public static double exp(double val) {
      final long tmp = (long) (1512775 * val + (1072693248 - 60801));
      return Double.longBitsToDouble(tmp << 32);
   }
   public static final double normalRelativeAngle(double d){
      d += Math.PI;
      double i = Math.floor(d*(1./(2.*Math.PI)));
      d -= i*(2.*Math.PI);
      return d-Math.PI;
   }
   public static final double normalAbsoluteAngle(double d){
      double i = Math.floor(d*(1./(2.*Math.PI)));
      d -= i*(2.*Math.PI);
      return d;
   }
}