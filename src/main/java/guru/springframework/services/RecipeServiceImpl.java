package guru.springframework.services;

import guru.springframework.commands.RecipeCommand;
import guru.springframework.converters.RecipeCommandToRecipe;
import guru.springframework.converters.RecipeToRecipeCommand;
import guru.springframework.domain.Recipe;
import guru.springframework.repositories.reactive.RecipeReactiveRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Created by jt on 6/13/17.
 */
@Slf4j
@Service
public class RecipeServiceImpl implements RecipeService {

    private final RecipeReactiveRepository recipeReactiveRepository;
    private final RecipeCommandToRecipe recipeCommandToRecipe;
    private final RecipeToRecipeCommand recipeToRecipeCommand;

    public RecipeServiceImpl(RecipeReactiveRepository recipeReactiveRepository, RecipeCommandToRecipe recipeCommandToRecipe, RecipeToRecipeCommand recipeToRecipeCommand) {
        this.recipeReactiveRepository = recipeReactiveRepository;
        this.recipeCommandToRecipe = recipeCommandToRecipe;
        this.recipeToRecipeCommand = recipeToRecipeCommand;
    }

    @Override
    public Flux<Recipe> getRecipes() {
        log.debug("I'm in the service");

        //Set<Recipe> recipeSet = new HashSet<>();
        //recipeReactiveRepository.findAll().iterator().forEachRemaining(recipeSet::add);

        return recipeReactiveRepository.findAll();
    }

    @Override
    public Mono<Recipe> findById(String id) {

        /*Recipe recipe = recipeReactiveRepository.findById(id).block();

        if (recipe == null) {
            throw new NotFoundException("Recipe Not Found. For ID value: " + id );
        }*/

        return recipeReactiveRepository.findById(id);
    }

    @Override
    @Transactional
    public Mono<RecipeCommand> findCommandById(String id) {

        return recipeReactiveRepository.findById(id).map(recipe -> {
            RecipeCommand recipeCommand = recipeToRecipeCommand.convert(recipe);

            recipeCommand.getIngredients().forEach(rc -> {
                rc.setRecipeId(recipeCommand.getId());
            });

            return recipeCommand;
        });


        /*RecipeCommand recipeCommand = recipeToRecipeCommand.convert(findById(id).block());

        //enhance command object with id value
        if(recipeCommand.getIngredients() != null && recipeCommand.getIngredients().size() > 0){
            recipeCommand.getIngredients().forEach(rc -> {
                rc.setRecipeId(recipeCommand.getId());
            });
        }

        return recipeCommand;*/
    }

    @Override
    @Transactional
    public Mono<RecipeCommand> saveRecipeCommand(RecipeCommand command) {
        //Recipe detachedRecipe = recipeCommandToRecipe.convert(command);

        return recipeReactiveRepository.save(recipeCommandToRecipe.convert(command)).map(recipeToRecipeCommand::convert);

        /*
        Recipe savedRecipe = recipeReactiveRepository.save(detachedRecipe).block();
        log.debug("Saved RecipeId:" + savedRecipe.getId());
        return recipeToRecipeCommand.convert(savedRecipe);*/
    }

    @Override
    public void deleteById(String idToDelete) {
        recipeReactiveRepository.deleteById(idToDelete).block();
    }
}
