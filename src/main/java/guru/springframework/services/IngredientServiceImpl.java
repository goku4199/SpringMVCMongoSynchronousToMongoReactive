package guru.springframework.services;

import guru.springframework.commands.IngredientCommand;
import guru.springframework.converters.IngredientCommandToIngredient;
import guru.springframework.converters.IngredientToIngredientCommand;
import guru.springframework.domain.Ingredient;
import guru.springframework.domain.Recipe;
import guru.springframework.repositories.RecipeRepository;
import guru.springframework.repositories.reactive.RecipeReactiveRepository;
import guru.springframework.repositories.reactive.UnitOfMeasureReactiveRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Created by jt on 6/28/17.
 */
@Slf4j
@Service
public class IngredientServiceImpl implements IngredientService {

    private final IngredientToIngredientCommand ingredientToIngredientCommand;
    private final IngredientCommandToIngredient ingredientCommandToIngredient;
    //private final RecipeRepository recipeRepository;
    private final UnitOfMeasureReactiveRepository unitOfMeasureReactiveRepository;

    private final RecipeReactiveRepository recipeReactiveRepository;

    public IngredientServiceImpl(IngredientToIngredientCommand ingredientToIngredientCommand,
                                 IngredientCommandToIngredient ingredientCommandToIngredient,
                                 /*RecipeRepository recipeRepository*/ UnitOfMeasureReactiveRepository unitOfMeasureReactiveRepository, RecipeReactiveRepository recipeReactiveRepository) {
        this.ingredientToIngredientCommand = ingredientToIngredientCommand;
        this.ingredientCommandToIngredient = ingredientCommandToIngredient;
        //this.recipeRepository = recipeRepository;
        this.unitOfMeasureReactiveRepository = unitOfMeasureReactiveRepository;
        this.recipeReactiveRepository = recipeReactiveRepository;
    }

    @Override
    public Mono<IngredientCommand> findByRecipeIdAndIngredientId(String recipeId, String ingredientId) {

        //REACTIVE API
        //Implementing in Reactive way taking out the Mono from RecipeReactiveRepository
        return recipeReactiveRepository.findById(recipeId).map(recipe -> recipe.getIngredients().stream().filter(ingredient -> ingredient.getId().equalsIgnoreCase(ingredientId)).findFirst()).filter(Optional::isPresent).map(ingredient -> {
            IngredientCommand command = ingredientToIngredientCommand.convert(ingredient.get());//getting the optional element out
            command.setRecipeId(recipeId);
            return command;//Converting Mono<Optional<Ingredient>> to Mono<IngredientCommand>
        });

        //Full Reactive Api can also use this
        /*return recipeReactiveRepository
                .findById(recipeId)
                .flatMapIterable(Recipe::getIngredients)
                .filter(ingredient -> ingredient.getId().equalsIgnoreCase(ingredientId))
                .single()
                .map(ingredient -> {
                    IngredientCommand command = ingredientToIngredientCommand.convert(ingredient);
                    command.setRecipeId(recipeId);
                    return command;
                });*/



        //JAVA8 Stream Implementation
        /*Optional<Recipe> recipeOptional = recipeRepository.findById(recipeId);

        if (!recipeOptional.isPresent()){
            //todo impl error handling
            log.error("recipe id not found. Id: " + recipeId);
        }

        Recipe recipe = recipeOptional.get();

        Optional<IngredientCommand> ingredientCommandOptional = recipe.getIngredients().stream()
                .filter(ingredient -> ingredient.getId().equals(ingredientId))
                .map( ingredient -> ingredientToIngredientCommand.convert(ingredient)).findFirst();

        if(!ingredientCommandOptional.isPresent()){
            //todo impl error handling
            log.error("Ingredient id not found: " + ingredientId);
        }

        //enhance command object with recipe id
        IngredientCommand ingredientCommand = ingredientCommandOptional.get();
        ingredientCommand.setRecipeId(recipe.getId());

        return Mono.just(ingredientCommandOptional.get());//REACTIVE*/
    }

    @Override
    public Mono<IngredientCommand> saveIngredientCommand(IngredientCommand command) {
        Recipe recipe = recipeReactiveRepository.findById(command.getRecipeId()).block();

        if (recipe == null) {/* TBDS */

            //todo toss error if not found!
            log.error("Recipe not found for id: " + command.getRecipeId());
            return Mono.just(new IngredientCommand());//REACTIVE
        } else {
            //Recipe recipe = recipeOptional.get();

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(command.getId()))
                    .findFirst();//getting reference of ingredient

            if (ingredientOptional.isPresent()) {
                Ingredient ingredientFound = ingredientOptional.get();
                ingredientFound.setDescription(command.getDescription());
                ingredientFound.setAmount(command.getAmount());
                ingredientFound.setUom(unitOfMeasureReactiveRepository//REACTIVE
                        .findById(command.getUom().getId()).block());
                        //.orElseThrow(() -> new RuntimeException("UOM NOT FOUND"))); //todo address this

                if (ingredientFound.getUom() == null){
                    new RuntimeException("UOM NOT FOUND");
                }

            } else {
                //add new Ingredient
                Ingredient ingredient = ingredientCommandToIngredient.convert(command);
                //  ingredient.setRecipe(recipe);
                recipe.addIngredient(ingredient);
            }

            Recipe savedRecipe = recipeReactiveRepository.save(recipe).block();//REACTIVE

            Optional<Ingredient> savedIngredientOptional = savedRecipe.getIngredients().stream()
                    .filter(recipeIngredients -> recipeIngredients.getId().equals(command.getId()))
                    .findFirst();

            //check by description
            if (!savedIngredientOptional.isPresent()) {
                //not totally safe... But best guess
                savedIngredientOptional = savedRecipe.getIngredients().stream()
                        .filter(recipeIngredients -> recipeIngredients.getDescription().equals(command.getDescription()))
                        .filter(recipeIngredients -> recipeIngredients.getAmount().equals(command.getAmount()))
                        .filter(recipeIngredients -> recipeIngredients.getUom().getId().equals(command.getUom().getId()))
                        .findFirst();
            }

            //todo check for fail

            //enhance with id value
            IngredientCommand ingredientCommandSaved = ingredientToIngredientCommand.convert(savedIngredientOptional.get());
            ingredientCommandSaved.setRecipeId(recipe.getId());

            return Mono.just(ingredientCommandSaved);//REACTIVE
        }

    }

    @Override
    public Mono<Void> deleteById(String recipeId, String idToDelete) {

        log.debug("Deleting ingredient: " + recipeId + ":" + idToDelete);

        Recipe recipe = recipeReactiveRepository.findById(recipeId).block();//ISSUE was resolved in later version on mongoDB

        //Recipe recipe = recipeRepository.findById(recipeId).get();

        if (recipe != null) {

            log.debug("found recipe");

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(idToDelete))
                    .findFirst();

            if (ingredientOptional.isPresent()) {
                log.debug("found Ingredient");

                recipe.getIngredients().remove(ingredientOptional.get());
                recipeReactiveRepository.save(recipe).block();//REACTIVE
            }
        } else {
            log.debug("Recipe Id Not found. Id:" + recipeId);
        }

        return Mono.empty();//REACTIVE


        /*
        Optional<Recipe> recipeOptional = recipeRepository.findById(recipeId);

        if(recipeOptional.isPresent()){
            Recipe recipe = recipeOptional.get();
            log.debug("found recipe");

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(idToDelete))
                    .findFirst();

            if(ingredientOptional.isPresent()){
                log.debug("found Ingredient");
                Ingredient ingredientToDelete = ingredientOptional.get();
               // ingredientToDelete.setRecipe(null);
                recipe.getIngredients().remove(ingredientOptional.get());
                recipeRepository.save(recipe);
            }
        } else {
            log.debug("Recipe Id Not found. Id:" + recipeId);
        }

        return Mono.empty();//REACTIVE
    }

         */
    }
}
