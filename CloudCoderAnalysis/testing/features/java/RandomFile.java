
public class RandomFile {
	public int[] zeroMax(int[] nums) {
		  for (int i=0; i<nums.length; i++) {
		     int max = 0;
		     if (nums[i]==0) {
		        for (int j=i; j<nums.length; j++) {
		           if (nums[j]%2==1 && nums[j]>max) {
		              max=nums[j];
		           }
		        }
		        nums[i]=max;
		     }
		   }
		   return nums;
		}

}
