export type Interpretation = {title:string|null;eventType:'MEETING'|'WORKSHOP'|null;eventDate:string|null;attendees:number|null;budgetCents:number|null;standardLunches:number|null;veganLunches:number|null;presentation:boolean|null;livestream:boolean|null;agendaSummary:string;questions:string[]};
export type Intake = {id:string;brief:string;referenceDate:string;status:string;interpretation:Interpretation|null;sessionId:string|null;message:string|null;attachmentName:string|null;eventId:string|null};
export function IntakeSource({draft}:{draft:Intake}) {
  return <section className="intake-source"><h2>{draft.eventId?'Original intake':'Review interpreted requirements'}</h2>
    <p className="muted">Reference date: {draft.referenceDate} · Pacific time. Confirm the exact event date below.</p>
    <details><summary>Source brief{draft.attachmentName?' and agenda':''}</summary><p className="brief-text">{draft.brief}</p>
      {draft.attachmentName&&<><p>{draft.attachmentName} · historical reference</p><a href={'/api/intakes/'+draft.id+'/agenda'} target="_blank" rel="noreferrer"><img className="agenda-preview" src={'/api/intakes/'+draft.id+'/agenda'} alt="Uploaded historical agenda"/></a></>}
    </details>
    {draft.message&&<p role="alert" className="error">{draft.message}</p>}
    {draft.status==='RUNNING'&&<p>Interpretation is still running. Refresh records to check its status.</p>}
    {draft.interpretation&&<><p className="brief-text">{draft.interpretation.agendaSummary}</p>{draft.interpretation.questions.length>0&&<><h3>{draft.eventId?'Notes from the original interpretation':'Details to resolve during review'}</h3><ul>{draft.interpretation.questions.map((q,i)=><li key={i}>{q}</li>)}</ul></>}
      <p className="muted">{draft.eventId?'Confirmed requirements and later revisions control assessments.':'Edit the form to resolve these notes, then explicitly confirm the supported requirements.'} The uploaded agenda does not change the fixed schedule.</p></>}
    {draft.sessionId&&<small className="session">Intake Loomspan session: {draft.sessionId}</small>}
  </section>;
}
