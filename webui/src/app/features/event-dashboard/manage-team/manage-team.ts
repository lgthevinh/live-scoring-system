import {Component, OnInit, signal, WritableSignal} from '@angular/core';
import {Team} from '../../../core/models/team.model';
import {FormsModule} from '@angular/forms';
import {TeamService} from '../../../core/services/team.service';

@Component({
  selector: 'app-manage-team',
  imports: [
    FormsModule
  ],
  templateUrl: './manage-team.html',
  styleUrl: './manage-team.css'
})
export class ManageTeam implements OnInit{
  teams: WritableSignal<Team[]> = signal([]);
  newTeam: Team = { teamId: '', teamName: '', teamSchool: '', teamRegion: '' };
  editTeam: Team = { teamId: '', teamName: '', teamSchool: '', teamRegion: '' };
  fileToUpload: File | null = null;
  constructor(
    private teamService: TeamService
  ) {
  }

  submitAddTeam() {
    if (this.newTeam.teamId && this.newTeam.teamName && this.newTeam.teamSchool && this.newTeam.teamRegion) {
      const createdTeam: Team = { ...this.newTeam };
      this.teamService.addTeam(createdTeam).subscribe({
        next: (response) => {
          const teamToAdd = response && response.teamId ? response : createdTeam;
          this.teams.update(teams => [...teams, teamToAdd]);
          console.log('Team added successfully');
          alert('Team added successfully');
          this.newTeam = { teamId: '', teamName: '', teamSchool: '', teamRegion: '' };
          const modal = document.getElementById('addTeamModal');
          if (modal) {
            (window as any).bootstrap.Modal.getInstance(modal)?.hide();
          }
        },
        error: (error) => {
          console.error('Error adding team:', error);
          alert('Error adding team: ' + error.message);
        },
      });
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
        alert('Team updated successfully');
      },
      error: (error) => {
        console.error('Error updating team:', error);
        alert('Error updating team: ' + error.message);
      },
    });
    // Hide the modal (Bootstrap 5)
    const modal = document.getElementById('editTeamModal');
    if (modal) {
      (window as any).bootstrap.Modal.getInstance(modal)?.hide();
    }
  }

  handleFileInput(files: FileList | null) {
    this.fileToUpload = files && files.length > 0 ? files[0] : null;
  }

  uploadTeamList() {

  }

  deleteTeam(teamId: string) {
    this.teamService.deleteTeam(teamId).subscribe({
      next: () => {
        this.teams.update(teams => teams.filter(t => t.teamId !== teamId));
        console.log('Team deleted successfully');
        alert('Team deleted successfully');
      },
      error: (error) => {
        console.error('Error deleting team:', error);
        alert('Error deleting team: ' + error.message);
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
      },
    });
  }
}
