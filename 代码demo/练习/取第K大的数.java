package 练习;

import java.util.Arrays;
import java.util.Random;

public class 取第K大的数 {

    static Random random = new Random();

    public static void main(String[] args) {
        int[] arr = {5,3,8,1,9,9,3,8,4,7,5,6,2,3,9,8,5,2,7,4,6};
        int k = 4;
        取第K大的数 funct = new 取第K大的数();
        //这里right写开区间，省去了各种+1-1
        int res = funct.findKthLargest(arr, 0, arr.length, k);
        System.out.println("第" + k + "大的数是：" + res);
        Arrays.sort(arr);
        for(int i=0;i<arr.length;i++){
            System.out.print(arr[i] + " ");
        }
    }

    int findKthLargest(int[] arr, int left, int right, int k) {
        if(left == right - 1) {
            return arr[left];
        }
        //随机取一个作为基准值
        int compare = arr[left + random.nextInt(right - left)];
        int index = left;
        //左边放小于，右边放大于等于
        for(int i=left;i < right;i++){
            if(arr[i] < compare){
                swap(arr, i, index);
                index++;
            }
        }
        //刚好是k，那么直接返回
        if(arr.length - k == index){
            return arr[index];
        } else if(arr.length - k > index){
            return findKthLargest(arr, index, right, k);
        } else {
            return findKthLargest(arr, left, index, k);
        }
    }

    private void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }


}
