import {Component, OnInit, signal, WritableSignal, inject} from '@angular/core';
import {Team} from '../../../core/models/team.model';
import {FormsModule} from '@angular/forms';
import {TeamService} from '../../../core/services/team.service';
import {ToastService} from '../../../core/services/toast.service';

@Component({
  selector: 'app-manage-team',
  imports: [
    FormsModule
  ],
  templateUrl: './manage-team.html',
  styleUrl: './manage-team.css'
})
export class ManageTeam implements OnInit{
  private teamService = inject(TeamService);
  private toastService = inject(ToastService);

  teams: WritableSignal<Team[]> = signal([]);
  newTeam: Team = { teamId: '', teamName: '', teamSchool: '', teamRegion: '' };
  editTeam: Team = { teamId: '', teamName: '', teamSchool: '', teamRegion: '' };
  fileToUpload: File | null = null;

  submitAddTeam() {
    if (this.newTeam.teamId && this.newTeam.teamName && this.newTeam.teamSchool && this.newTeam.teamRegion) {
      this.teamService.addTeam(this.newTeam).subscribe({
        next: () => {
          this.teams.update(teams => [...teams, { ...this.newTeam }]);
          console.log('Team added successfully');
          this.toastService.show('Team added successfully', 'success');
        },
        error: (error) => {
          console.error('Error adding team:', error);
          this.toastService.show('Error adding team: ' + error.message, 'error');
        },
      });
      // Reset the newTeam object
      this.newTeam = { teamId: '', teamName: '', teamSchool: '', teamRegion: '' };
      // Hide the modal
      const modal = document.getElementById('addTeamModal');
      if (modal) {
        // Bootstrap 5 modal instance
        (window as any).bootstrap.Modal.getInstance(modal).hide();
      }
    }
  }

  openEditTeamModal(team: Team) {
    // Clone the team to avoid mutating the table row before saving
    this.editTeam = { ...team };
  }

  submitEditTeam() {
    const index = this.teams().findIndex(t => t.teamId === this.editTeam.teamId);
    if (index !== -1) {
      this.teams.update(teams => {
        const updatedTeams = [...teams];
        updatedTeams[index] = { ...this.editTeam };
        return updatedTeams;
      });
    }
    this.teamService.updateTeam(this.editTeam).subscribe({
      next: () => {
        console.log('Team updated successfully');
        this.toastService.show('Team updated successfully', 'success');
      },
      error: (error) => {
        console.error('Error updating team:', error);
        this.toastService.show('Error updating team: ' + error.message, 'error');
      },
    });
    // Hide the modal (Bootstrap 5)
    const modal = document.getElementById('editTeamModal');
    if (modal) {
      (window as any).bootstrap.Modal.getInstance(modal)?.hide();
    }
  }

  handleFileInput(event: Event) {
    const input = event.target as HTMLInputElement;
    this.fileToUpload = input.files && input.files.length > 0 ? input.files[0] : null;
  }

  uploadTeamList() {
    if (!this.fileToUpload) {
      this.toastService.show('Please select a CSV file to import', 'info');
      return;
    }

    if (!this.fileToUpload.name.endsWith('.csv')) {
      this.toastService.show('Please select a valid CSV file', 'error');
      return;
    }

    this.teamService.importTeams(this.fileToUpload).subscribe({
      next: (response) => {
        this.toastService.show(response.message || 'Teams imported successfully', 'success');
        this.fileToUpload = null;
        // Reload teams list
        this.teamService.getTeams().subscribe({
          next: (teams: Team[]) => {
            this.teams.set(teams);
          },
          error: (error) => {
            console.error('Error reloading teams:', error);
          }
        });
      },
      error: (error) => {
        console.error('Error importing teams:', error);
        this.toastService.show('Error importing teams: ' + (error.error?.error || error.message), 'error');
      }
    });
  }

  exportTeamList() {
    this.teamService.exportTeams().subscribe({
      next: (blob: Blob) => {
        // Check if it's an error response by reading first few bytes
        blob.text().then(text => {
          if (text.trim().startsWith('{')) {
            // It's an error JSON
            try {
              const errorData = JSON.parse(text);
              this.toastService.show(errorData.error || 'Export failed', 'error');
            } catch (e) {
              this.toastService.show('Export failed: ' + text, 'error');
            }
          } else {
            // It's the CSV data
            const csvBlob = new Blob([text], { type: 'text/csv' });
            const url = window.URL.createObjectURL(csvBlob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `teams_${new Date().toISOString().split('T')[0]}.csv`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            this.toastService.show('Teams exported successfully', 'success');
          }
        });
      },
      error: (error) => {
        console.error('Error exporting teams:', error);
        this.toastService.show('Error exporting teams: ' + error.message, 'error');
      }
    });
  }

  downloadTemplate() {
    const template = 'Team ID,Team Name,School,Region\n' +
                     'T001,Team Alpha,High School A,North\n' +
                     'T002,Team Beta,High School B,South\n';
    const blob = new Blob([template], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'team_import_template.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
    this.toastService.show('Template downloaded', 'success');
  }

  deleteTeam(teamId: string) {
    // Store team for potential undo
    const deletedTeam = this.teams().find(t => t.teamId === teamId);

    this.teamService.deleteTeam(teamId).subscribe({
      next: () => {
        this.teams.update(teams => teams.filter(t => t.teamId !== teamId));
        console.log('Team deleted successfully');
        this.toastService.show(`Team ${deletedTeam?.teamId || ''} deleted`, 'success', 15000, {
          label: 'Undo',
          onAction: () => {
            if (deletedTeam) {
              this.teamService.addTeam(deletedTeam).subscribe(() => {
                this.teams.update(teams => [...teams, deletedTeam]);
                this.toastService.show('Team restored', 'success');
              });
            }
          }
        });
      },
      error: (error) => {
        console.error('Error deleting team:', error);
        this.toastService.show('Error deleting team: ' + error.message, 'error');
      },
    });
  }

  ngOnInit(): void {
    // Load initial teams - in real app, this would come from a service
    this.teamService.getTeams().subscribe({
      next: (teams: Team[]) => {
        this.teams.set(teams);
      },
      error: (error) => {
        console.error('Error loading teams:', error);
        this.toastService.show('Error loading teams: ' + error.message, 'error');
      },
    });
  }
}
