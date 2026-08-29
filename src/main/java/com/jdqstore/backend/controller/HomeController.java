package com.jdqstore.backend.controller;

import com.jdqstore.backend.entity.*;
import com.jdqstore.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HomeController {
    private final HeroSlideRepository heroSlideRepository;
    private final RouletteSettingRepository rouletteSettingRepository;
    private final MysteryBoxSettingRepository mysteryBoxSettingRepository;
    private final HomeCategoryCollectionRepository homeCategoryCollectionRepository;

    @GetMapping("/hero")
    public ResponseEntity<HeroConfigResponse> getHeroConfig() {
        List<HeroSlide> slides = heroSlideRepository.findAll();
        return ResponseEntity.ok(new HeroConfigResponse(slides));
    }



    @GetMapping("/roulette-setting")
    public ResponseEntity<RouletteSetting> getRouletteSetting() {
        RouletteSetting setting = rouletteSettingRepository.findById(1L).orElseGet(() -> {
            RouletteSetting rs = RouletteSetting.builder()
                .id(1L)
                .activeDays("WEDNESDAY")
                .values("5,10,15,20,25,10,5,15")
                .probabilities("12.5,12.5,12.5,12.5,12.5,12.5,12.5,12.5")
                .build();
            return rouletteSettingRepository.save(rs);
        });
        return ResponseEntity.ok(setting);
    }

    @PutMapping("/roulette-setting")
    public ResponseEntity<RouletteSetting> updateRouletteSetting(@RequestBody RouletteSetting setting) {
        setting.setId(1L);
        return ResponseEntity.ok(rouletteSettingRepository.save(setting));
    }

    @GetMapping("/mystery-box")
    public ResponseEntity<MysteryBoxSetting> getMysteryBoxSetting() {
        MysteryBoxSetting setting = mysteryBoxSettingRepository.findById(1L).orElseGet(() -> {
            MysteryBoxSetting mb = MysteryBoxSetting.builder()
                .id(1L)
                .title("Caja Misteriosa")
                .description("Recibe de 2 a 3 prendas exclusivas seleccionadas de nuestra última colección. ¡Edición limitada con prendas de valor superior al costo de la caja!")
                .price(90000.0)
                .estimatedValue("+$160.000")
                .revealedSubtext("2-3 Prendas Premium Sorpresa")
                .perk1("Contiene de 2 a 3 prendas premium.")
                .perk2("Empaque de regalo oficial de edición limitada.")
                .perk3("Garantía de prendas auténticas 100% de la marca.")
                .sizes("S,M,L,XL,XXL")
                .active(true)
                .build();
            return mysteryBoxSettingRepository.save(mb);
        });
        return ResponseEntity.ok(setting);
    }

    @PutMapping("/mystery-box")
    public ResponseEntity<MysteryBoxSetting> updateMysteryBoxSetting(@RequestBody MysteryBoxSetting setting) {
        setting.setId(1L);
        return ResponseEntity.ok(mysteryBoxSettingRepository.save(setting));
    }

    @PostMapping("/hero/slides")
    public ResponseEntity<HeroSlide> addSlide(@RequestBody HeroSlide slide) {
        return ResponseEntity.ok(heroSlideRepository.save(slide));
    }

    @PutMapping("/hero/slides/{id}")
    public ResponseEntity<HeroSlide> updateSlide(@PathVariable Long id, @RequestBody HeroSlide slide) {
        slide.setId(id);
        return ResponseEntity.ok(heroSlideRepository.save(slide));
    }

    @DeleteMapping("/hero/slides/{id}")
    public ResponseEntity<Void> deleteSlide(@PathVariable Long id) {
        heroSlideRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/collections")
    public ResponseEntity<List<HomeCategoryCollection>> getCollections() {
        return ResponseEntity.ok(homeCategoryCollectionRepository.findAllByOrderByDisplayOrderAsc());
    }

    @PostMapping("/collections")
    public ResponseEntity<HomeCategoryCollection> createCollection(@RequestBody HomeCategoryCollection item) {
        return ResponseEntity.ok(homeCategoryCollectionRepository.save(item));
    }

    @PutMapping("/collections/{id}")
    public ResponseEntity<HomeCategoryCollection> updateCollection(@PathVariable Long id, @RequestBody HomeCategoryCollection item) {
        item.setId(id);
        return ResponseEntity.ok(homeCategoryCollectionRepository.save(item));
    }

    @DeleteMapping("/collections/{id}")
    public ResponseEntity<Void> deleteCollection(@PathVariable Long id) {
        homeCategoryCollectionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @Data
    @AllArgsConstructor
    public static class HeroConfigResponse {
        private List<HeroSlide> slides;
    }
}
