package com.minidb;

import java.util.Scanner;

public class Main {
    public static void main(String args[]) {
        Scanner input = new Scanner(System.in);

        while(true){
            printPrompt();
            String userPrompt = input.nextLine().toLowerCase();

            if(userPrompt.equals("exit()")){
                input.close();
                System.exit(0);
            }else{
                System.out.print("Executing: "+userPrompt);
            }

            System.out.println();
        }
    }

    public static void printPrompt(){
        System.out.print("minidb> ");
    }
}
