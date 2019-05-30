import React from 'react';
import {render} from 'react-dom';
import {BrowserRouter, Link, Route, Switch} from 'react-router-dom';
import Raven from 'raven-js';
import axios from 'axios';

import NotFoundComponent from './NotFoundComponent.jsx';
import FrontPage from './FrontPage.jsx';
import NewFrontPage from './NewFrontPage.jsx';

import { library } from '@fortawesome/fontawesome-svg-core'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faHdd, faUserAlt, faMinusCircle, faExclamationCircle, faExclamationTriangle, faCheck, faCheckCircle } from '@fortawesome/free-solid-svg-icons'
import { faChevronCircleDown,faChevronCircleRight,faTrashAlt, faFilm, faVolumeUp,faImage, faFile } from '@fortawesome/free-solid-svg-icons'

library.add(faHdd, faUserAlt, faMinusCircle, faExclamationCircle, faExclamationTriangle, faCheck, faCheckCircle);
library.add(faFilm, faVolumeUp, faImage, faFilm, faFile);
window.React = require('react');

class App extends React.Component {
    constructor(props){
        super(props);
        axios.get("/system/publicdsn").then(response=> {
            Raven
                .config(response.data.publicDsn)
                .install();
            console.log("Sentry initialised for " + response.data.publicDsn);
        }).catch(error => {
            console.error("Could not intialise sentry", error);
        });

    }

    render(){
        return <div>
            <Switch>
                <Route path="/" exact={true} component={NewFrontPage}/>
                <Route default component={NotFoundComponent}/>
            </Switch>
        </div>
    }
}

render(<BrowserRouter root="/"><App/></BrowserRouter>, document.getElementById('app'));
