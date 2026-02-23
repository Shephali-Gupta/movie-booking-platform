package com.movie.booking.service;

import com.movie.booking.dto.ShowRequest;
import com.movie.booking.model.Screen;
import com.movie.booking.model.Show;
import com.movie.booking.repository.ScreenRepository;
import com.movie.booking.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShowManagementService")
class ShowManagementServiceTest {

    @Mock private ShowRepository showRepository;
    @Mock private ScreenRepository screenRepository;

    private ShowManagementService showManagementService;
    private static final Long THEATRE_ID = 1L;
    private static final Long SCREEN_ID = 10L;
    private static final Long SHOW_ID = 100L;

    @BeforeEach
    void setUp() {
        showManagementService = new ShowManagementService(showRepository, screenRepository);
    }

    @Nested
    @DisplayName("createShow")
    class CreateShow {

        @Test
        @DisplayName("success: creates show and returns saved entity")
        void success() {
            ShowRequest request = new ShowRequest();
            request.setMovieId(1L);
            request.setScreenId(SCREEN_ID);
            request.setShowTime(Instant.now());
            request.setPrice(new BigDecimal("150.00"));
            Show saved = new Show();
            saved.setId(SHOW_ID);
            saved.setMovieId(1L);
            saved.setScreenId(SCREEN_ID);
            saved.setPrice(request.getPrice());
            when(showRepository.save(any(Show.class))).thenReturn(saved);

            Show result = showManagementService.createShow(request);

            assertThat(result.getId()).isEqualTo(SHOW_ID);
            assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
            verify(showRepository).save(argThat(s ->
                    s.getMovieId() == 1L && s.getScreenId() == SCREEN_ID));
        }
    }

    @Nested
    @DisplayName("updateShow")
    class UpdateShow {

        @Test
        @DisplayName("failure: throws when show not found")
        void showNotFound() {
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.empty());
            ShowRequest request = new ShowRequest();

            assertThatThrownBy(() -> showManagementService.updateShow(SHOW_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Show not found");
            verify(showRepository, never()).save(any());
        }

        @Test
        @DisplayName("success: updates only non-null fields")
        void success() {
            Show existing = new Show();
            existing.setId(SHOW_ID);
            existing.setPrice(new BigDecimal("100"));
            existing.setShowTime(Instant.now());
            ShowRequest request = new ShowRequest();
            request.setPrice(new BigDecimal("120"));
            when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(existing));
            when(showRepository.save(any(Show.class))).thenAnswer(i -> i.getArgument(0));

            Show result = showManagementService.updateShow(SHOW_ID, request);

            assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("120"));
            verify(showRepository).save(existing);
        }
    }

    @Nested
    @DisplayName("deleteShow")
    class DeleteShow {

        @Test
        @DisplayName("failure: throws when show not found")
        void showNotFound() {
            when(showRepository.existsById(SHOW_ID)).thenReturn(false);

            assertThatThrownBy(() -> showManagementService.deleteShow(SHOW_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Show not found");
            verify(showRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("success: deletes show")
        void success() {
            when(showRepository.existsById(SHOW_ID)).thenReturn(true);

            showManagementService.deleteShow(SHOW_ID);

            verify(showRepository).deleteById(SHOW_ID);
        }
    }

    @Nested
    @DisplayName("isScreenOwnedByTheatre")
    class IsScreenOwnedByTheatre {

        @Test
        @DisplayName("returns true when screen belongs to theatre")
        void success() {
            Screen screen = new Screen();
            screen.setId(SCREEN_ID);
            screen.setTheatreId(THEATRE_ID);
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.of(screen));

            boolean result = showManagementService.isScreenOwnedByTheatre(SCREEN_ID, THEATRE_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when screen belongs to different theatre")
        void wrongTheatre() {
            Screen screen = new Screen();
            screen.setId(SCREEN_ID);
            screen.setTheatreId(999L);
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.of(screen));

            boolean result = showManagementService.isScreenOwnedByTheatre(SCREEN_ID, THEATRE_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when screen not found")
        void screenNotFound() {
            when(screenRepository.findById(SCREEN_ID)).thenReturn(Optional.empty());

            boolean result = showManagementService.isScreenOwnedByTheatre(SCREEN_ID, THEATRE_ID);

            assertThat(result).isFalse();
        }
    }
}
