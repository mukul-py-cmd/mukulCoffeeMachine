package service;

import error.handler.ErrorHandler;
import ingredientInventory.IngredientManager;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import model.Beverage;
import model.CoffeeMachineDetails;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import tasks.BeverageMakerTask;

/**
 * Assumptions:
 * 1) Input Json is valid and in format given in question.
 * 
 * Algorithm:
 * n threads are invoked to represent n outlets of coffee machine. Only n threads run at a single point of time.
 * Every outlet is assigned a queue, threads are made for every order,  now these threads are uniformly distributed in every queue.
 * At a single point of time, threads can't make beverages for same ingredients.
 */

/**
 * Singleton Class to simulate a CoffeeMachine
*/

@Slf4j
public class CoffeeMachine {

    private static CoffeeMachine coffeeMachine;
    private static final int MAX_QUEUED_REQUEST = 100;
    private ThreadPoolExecutor executor;
    private Object lock = new Object();

    public CoffeeMachine(int outlet) {
        System.out.println("New Machine");
        executor = new ThreadPoolExecutor(outlet, outlet, 1000L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(MAX_QUEUED_REQUEST));
        executor.setRejectedExecutionHandler(new ErrorHandler());
    }

    public void fillInventory(CoffeeMachineDetails coffeeMachineDetails) {
        IngredientManager ingredientManager = IngredientManager.getInstance();
        Map<String, Integer> ingredients = coffeeMachineDetails.getMachine().getIngredientQuantityMap();
        for (String key : ingredients.keySet()) {
            ingredientManager.addInventory(key, ingredients.get(key));
        }
    }
    public void serveOrders(Map<String, HashMap<String, Integer>> beverages) {
        for (String key : beverages.keySet()) {
            Beverage beverage = new Beverage(key, beverages.get(key));
            addBeverageRequest(beverage);
        }
    }

    private void addBeverageRequest(Beverage beverage) {
        BeverageMakerTask task = new BeverageMakerTask(beverage) {
            @SneakyThrows
            @Override
            public void run() {
                if (checkAndUpdateInventory(beverage)) {
                    System.out.println(beverage.getName() + " is prepared");
                }
            }
        };
        executor.execute(task);
    }

    public void stopMachine() {
        executor.shutdown();
    }

    
    public void reset() {
        log.info("Resetting");
        this.stopMachine();
        IngredientManager.getInstance().resetInventory();
    }

    //Making this thread safe by synchronizing
    private boolean checkAndUpdateInventory(Beverage beverage) {
        IngredientManager inventory = IngredientManager.getInstance();
        Map<String, Integer> requiredIngredientMap = beverage.getIngredientQuantityMap();
        boolean isPossible = true;

        synchronized (lock) {
            for (String ingredient : requiredIngredientMap.keySet()) {
                int ingredientInventoryCount = inventory.get(ingredient);
                if (ingredientInventoryCount == -1 || ingredientInventoryCount == 0) {
                    System.out.println(
                        beverage.getName() + " cannot be prepared because " + ingredient
                            + " is not available");
                    isPossible = false;
                    break;
                } else if (requiredIngredientMap.get(ingredient) > ingredientInventoryCount) {
                    System.out.println(
                        beverage.getName() + " cannot be prepared because " + ingredient
                            + " is not sufficient");
                    isPossible = false;
                    break;
                }
            }

            if (isPossible) {
                for (String ingredient : requiredIngredientMap.keySet()) {
                    inventory.reduceQuantity(ingredient, requiredIngredientMap.get(ingredient));
                    if(inventory.get(ingredient) == 0) {
                        log.warn(ingredient+" is empty. Please refill");
                    }
                }
            }
        }

        return isPossible;
    }
}
