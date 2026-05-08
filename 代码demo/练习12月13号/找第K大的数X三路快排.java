package 练习12月13号;

import java.util.Random;

public class 找第K大的数X三路快排 {

    Random random = new Random();

    public static void main(String[] args) {
        int[] arr = {5, 7, 1, 3, 4, 9, 10, 6, 2, 8};
        int k = 6;
        找第K大的数X三路快排 快排 = new 找第K大的数X三路快排();
        // 闭区间
        int res = 快排.findKthLargest(arr, 0, arr.length - 1, arr.length - k + 1);
        System.out.println(res);
    }

    public int findKthLargest(int[] nums, int left, int right, int k) {
        if(left == right) {
            return nums[left];
        }
        int compareIndex = random.nextInt(right - left + 1) + left;
        int compareNum = nums[compareIndex];
        //[left, lt) [lt, rt],(rt,right]
        int lt = left;
        int index = left;
        int rt = right;
        while(index <= rt) {
            if(nums[index] < compareNum) {
                swap(nums, index, lt);
                index ++;
                lt ++;
            } else if(nums[index] > compareNum) {
                swap(nums, index, rt);
                rt --;
                //注意这里是关键不同点，index不用++，因为换过来的数据没有被遍历过，不知道大小
            } else {
                index ++;
            }
        }

        int leftSize = lt - left;
        int midSize = rt - lt + 1;
        if(k <= leftSize) {
            return findKthLargest(nums, left, lt - 1, k);
        } else if(k <= leftSize + midSize) {
            return compareNum;
        } else {
            return findKthLargest(nums, rt + 1, right, k - leftSize - midSize);
        }
    }

    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
}
